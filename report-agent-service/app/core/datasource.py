"""RDS 조회 계층 — 보고서가 쓰는 모든 데이터는 여기를 거친다.

설계 두 가지가 핵심이다.

1) **범위 조회** — 필요한 행만 SQL 로 가져온다(query 의 eq/isin/range).
   전체 테이블을 메모리에 올리던 방식은 scada_record 처럼 큰 테이블에서 성립하지 않는다
   (보고서 1건에 14만 행을 읽고 파이썬에서 잘라내던 구조였다).

2) **스냅샷 일관성** — 보고서 1건이 읽는 여러 테이블은 한 트랜잭션에서 읽는다(snapshot()).
   표와 종합분석이 서로 다른 시점의 데이터를 인용하면 critic 검증이 흔들리고,
   SageMaker 적재 배치가 도는 중이면 실제로 어긋난 조합이 나온다.
   service.generate_report() 가 보고서 1건 전체를 이 블록으로 감싼다.

CSV 경로는 없다. 데이터 소스는 RDS 뿐이고, data/*.csv 는 scripts/seed_rds.py 의
적재 원본으로만 쓰인다(조회에는 관여하지 않는다).
"""
import threading
from contextlib import contextmanager

import pandas as pd
from sqlalchemy import text

from app.core.config import DB_URL

# 논리 테이블명 → (물리 테이블명, 조회 컬럼). 컬럼을 명시로 고정한다 — "SELECT *" 로 두면
# DB 에 컬럼이 추가될 때 DataFrame 모양이 조용히 바뀌어 집계가 소리 없이 달라진다.
_RDS_TABLES = {
    "turbine": ("turbine", (
        "turbine_id", "wind_farm_id", "turbine_model_id", "turbine_code",
        "turbine_latitude", "turbine_longitude", "installation_date", "created_at",
    )),
    "blade": ("blade", (
        "blade_id", "turbine_id", "blade_tag", "installation_date", "created_at",
    )),
    "wind_farm": ("wind_farm", (
        "wind_farm_id", "wind_farm_name", "wind_farm_latitude", "wind_farm_longitude",
        "capacity", "installation_date", "address", "created_at",
        "aws_station_id", "asos_station_id",
    )),
    "turbine_model": ("turbine_model", (
        "turbine_model_id", "model", "manufacturer", "rated_power", "rotor_diameter",
        "hub_height", "blade_length", "cut_in_speed", "rated_speed", "cut_out_speed",
    )),
    # user 는 인증 컬럼(password·employee_id 등)까지 갖고 있다 — 보고서가 쓰는 것만 고른다.
    "user": ("user", ("user_id", "user_name", "role", "status")),

    # ── 결함 진단(defect) 체인 ───────────────────────────────────────────────
    # report 는 DB 쪽이 더 넓지만 보고서가 읽는 컬럼만 고른다(period_*·context 등은 조회에 안 쓴다).
    "report": ("report", ("report_id", "report_type", "status")),
    "inspection": ("inspection", (
        "inspection_id", "turbine_id", "user_id", "report_id",
        "inspection_start", "inspection_end", "status", "created_at",
    )),
    "defect": ("defect", (
        "defect_id", "inspection_id", "blade_id", "defect_type", "severity", "part_side",
        "bbox_x", "bbox_y", "bbox_w", "bbox_h", "confidence", "image_path", "created_at",
    )),

    # ── 이상감지(anomaly) ────────────────────────────────────────────────────
    # event_type·scope 는 RDS 가 대문자 관례(PROLONGED_STOP/FARM)로 저장 → LOWER 로 코드(소문자)에 맞춘다.
    # (tier 는 A/B 그대로. LOWER 는 이미 소문자면 무해한 no-op.)
    "anomaly_event": ("anomaly_event", (
        "event_id", "turbine_id", "start_time", "end_time", "expected_power",
        "actual_power", "z_score", "deviation_pct", "tier",
        "LOWER(event_type) AS event_type", "LOWER(scope) AS scope",
        "energy_ratio_30d", "estimated_loss_kwh", "created_at",
    )),
    "scada_record": ("scada_record", (
        "turbine_id", "recorded_at", "wind_speed", "power_output", "air_density",
        "norm_wind_speed", "is_stopped", "train_mask",
        "expected_power_pooled", "expected_power_unit",
    )),
    "aws_record": ("aws_record", (
        "aws_station_id", "recorded_at", "temperature", "pressure",
        "humidity", "wind_direction", "precipitation",
    )),
    "asos_record": ("asos_record", (
        "asos_station_id", "recorded_at", "sd_hr3", "sd_day", "sd_tot",
    )),
}

# 테이블별 날짜 컬럼. DATE 를 date 객체로 돌려주는 드라이버가 있어 dtype 이 갈리므로 여기서 통일한다.
_DATE_COLS = {
    "anomaly_event": ("start_time", "end_time", "created_at"),
    "aws_record": ("recorded_at",),
    "asos_record": ("recorded_at",),
    "blade": ("installation_date", "created_at"),
    "defect": ("created_at",),
    "inspection": ("inspection_start", "inspection_end", "created_at"),
    "scada_record": ("recorded_at",),
    "turbine": ("installation_date", "created_at"),
    "wind_farm": ("installation_date", "created_at"),
}

# 전체를 캐시해도 되는 참조표(차원 테이블). 작고 보고서 도중 바뀌지 않는다.
# 사실 테이블(scada_record·anomaly_event·defect…)은 캐시하지 않는다 — 범위 조회로 그때그때 읽고,
# 한 보고서 안의 일관성은 snapshot() 트랜잭션이 보장한다.
_REFERENCE = frozenset({"turbine", "wind_farm", "turbine_model", "blade", "user"})

_engine = None
_engine_lock = threading.Lock()
_cache = {}
_cache_lock = threading.RLock()   # turbine_index() 가 락을 쥔 채 load_table() 을 부른다
_local = threading.local()        # 진행 중인 snapshot 커넥션
_reads = {}                       # 테이블명 → [조회 횟수, 읽은 행 수] (계측용)


def _get_engine():
    """SQLAlchemy 엔진(프로세스당 1개)."""
    global _engine
    if _engine is not None:
        return _engine
    with _engine_lock:
        if _engine is None:
            if not DB_URL:
                raise RuntimeError("DB_URL 이 비어 있다 (.env 또는 환경변수 확인).")
            from sqlalchemy import create_engine
            # pool_pre_ping: RDS 가 유휴 커넥션을 끊어도 다음 쿼리에서 조용히 재연결한다.
            # pool_recycle: wait_timeout(기본 8h) 보다 먼저 커넥션을 버려 끊긴 소켓 재사용을 막는다.
            _engine = create_engine(DB_URL, pool_pre_ping=True, pool_recycle=3600)
    return _engine


@contextmanager
def snapshot():
    """이 블록 안의 모든 조회를 한 트랜잭션에서 수행한다 — 테이블 간 시점이 어긋나지 않는다.

    MySQL 기본 격리수준이 REPEATABLE READ 라, 트랜잭션 첫 조회 시점의 일관된 읽기 뷰가
    블록이 끝날 때까지 유지된다. 중첩 호출은 바깥 트랜잭션을 그대로 쓴다.
    """
    if getattr(_local, "conn", None) is not None:
        yield
        return
    with _get_engine().connect() as conn:
        with conn.begin():
            _local.conn = conn
            try:
                yield
            finally:
                _local.conn = None


def _output_columns(name: str) -> set:
    """SELECT 절이 실제로 내보내는 컬럼명. 'LOWER(x) AS x' 같은 표현식은 별칭을 쓴다."""
    cols = set()
    for c in _RDS_TABLES[name][1]:
        cols.add(c.rsplit(" AS ", 1)[-1].strip() if " AS " in c else c)
    return cols


def _check_column(name: str, col: str):
    """필터 컬럼이 실재하는지 확인한다. 식별자가 SQL 에 직접 들어가므로 주입 방어이기도 하다."""
    if col not in _output_columns(name):
        raise ValueError(f"'{name}' 에 없는 컬럼으로 필터할 수 없다: {col}")


def query(name: str, *, eq=None, isin=None, span=None) -> pd.DataFrame:
    """테이블 1개를 조건으로 조회한다. 값은 전부 바인드 파라미터로 넘어간다.

        query("scada_record",
              eq={"turbine_id": 11},
              span={"recorded_at": (start, end_excl)})   # start <= x < end_excl

    eq   : {컬럼: 값}          동등 비교
    isin : {컬럼: [값, ...]}   IN. 빈 리스트면 빈 결과를 즉시 돌려준다(WHERE IN () 는 문법 오류)
    span : {컬럼: (하한, 상한)} 하한 이상 ~ 상한 미만. None 이면 그 방향 무제한
           내장 range 를 가리지 않으려고 span 으로 뒀다(BETWEEN 은 양끝 포함이라 이름이 안 맞는다).

    조건을 하나도 안 주면 전체 조회다. 큰 테이블에 그렇게 부르지 말 것.
    """
    table, cols = _RDS_TABLES[name]
    where, params = [], {}

    for col, v in (eq or {}).items():
        _check_column(name, col)
        params[f"eq_{col}"] = v
        where.append(f"`{col}` = :eq_{col}")

    for col, vals in (isin or {}).items():
        _check_column(name, col)
        vals = list(vals)
        if not vals:
            # IN () 는 MySQL 문법 오류다. 어차피 결과가 없으므로 빈 프레임을 만들어 돌려준다.
            return _empty(name)
        keys = []
        for i, v in enumerate(vals):
            params[f"in_{col}_{i}"] = v
            keys.append(f":in_{col}_{i}")
        where.append(f"`{col}` IN ({', '.join(keys)})")

    for col, (lo, hi) in (span or {}).items():
        _check_column(name, col)
        if lo is not None:
            params[f"lo_{col}"] = lo
            where.append(f"`{col}` >= :lo_{col}")
        if hi is not None:
            params[f"hi_{col}"] = hi
            where.append(f"`{col}` < :hi_{col}")

    sql = f"SELECT {', '.join(cols)} FROM `{table}`"
    if where:
        sql += " WHERE " + " AND ".join(where)

    conn = getattr(_local, "conn", None)
    df = pd.read_sql(text(sql), conn if conn is not None else _get_engine(), params=params)
    return _normalize(name, df)


def _normalize(name: str, df: pd.DataFrame) -> pd.DataFrame:
    for c in _DATE_COLS.get(name, ()):
        if c in df.columns:
            df[c] = pd.to_datetime(df[c], errors="coerce")
    stat = _reads.setdefault(name, [0, 0])
    stat[0] += 1
    stat[1] += len(df)
    return df


def _empty(name: str) -> pd.DataFrame:
    """조건상 결과가 없을 때 쓰는 빈 프레임. 컬럼 구성은 실제 조회와 같아야 한다."""
    df = pd.DataFrame({c: pd.Series(dtype="object") for c in sorted(_output_columns(name))})
    return _normalize(name, df[[c for c in _output_columns(name)]])


def load_table(name: str) -> pd.DataFrame:
    """테이블 전체 조회. 참조표(_REFERENCE)는 캐시하고 나머지는 매번 읽는다.

    캐시된 객체를 그대로 돌려주므로 호출부는 in-place 로 수정하면 안 된다(.copy() 후 수정).
    """
    if name in _REFERENCE:
        return cached(f"table:{name}", lambda: query(name))
    return query(name)


def cached(key: str, build):
    """참조표·파생 프레임용 캐시. 프로세스 수명 동안 유지된다.

    사실 테이블은 여기 담지 않는다 — 캐시 시점이 테이블마다 어긋나는 것이 #89 의 원인이었다.
    참조표(터빈·단지 목록)는 보고서 도중 바뀌지 않으므로 캐시해도 일관성 문제가 없다.
    """
    if key in _cache:
        return _cache[key]
    with _cache_lock:
        if key not in _cache:
            _cache[key] = build()
        return _cache[key]


def reset_cache():
    """캐시와 계측을 비운다. 데이터 적재 직후 강제 갱신이나 테스트에서 쓴다."""
    with _cache_lock:
        _cache.clear()
        _reads.clear()


def reads() -> dict:
    """이번 실행에서 테이블별로 몇 번·몇 행을 읽었는지. {name: (횟수, 행수)}

    범위 조회가 실제로 좁혀졌는지 확인하는 용도다. scripts/check_sources.py 가 쓴다.
    """
    return {k: tuple(v) for k, v in _reads.items()}


def turbine_index() -> pd.DataFrame:
    """터빈 참조표 — turbine_id, turbine_code, wind_farm_id, wind_farm_name.

    ERD 상 각 테이블은 코드·이름값 없이 FK 만 갖는다. 그 FK 를 사람이 읽는 값으로 푸는 조인은
    여기 한 곳에만 둔다. how="left" — wind_farm FK 가 깨져도 터빈 행을 잃지 않는다(이름만 빈다).
    """
    return cached("turbine_index", lambda: load_table("turbine")[
        ["turbine_id", "turbine_code", "wind_farm_id"]
    ].merge(
        load_table("wind_farm")[["wind_farm_id", "wind_farm_name"]],
        on="wind_farm_id",
        how="left",
    ))


def table_available(name: str) -> bool:
    """조회 가능한 테이블인가. 등재돼 있으면 True."""
    return name in _RDS_TABLES
