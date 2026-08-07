"""테이블 단위 데이터 소스 — CSV(로컬) ↔ RDS(배포) 전환 지점.

조회 코드는 load_table(name) 하나만 쓰고, 어디서 읽어오는지는 여기서 정한다.
반환 DataFrame 의 컬럼명·순서·dtype 은 소스와 무관하게 항상 CSV 기준이므로 집계 코드는 불변이다.

RDS 로 전환되는 테이블은 _RDS_TABLES 에 등재된 것뿐이다. 등재되지 않은 테이블은
DATA_SOURCE=="rds" 여도 CSV 로 읽는다 — 아직 RDS 에 없거나, 있어도 컬럼이 모자라기 때문:
  report / inspection / defect / anomaly_event / aws_record
      → backend 마이그레이션(V1~V3)에 테이블 자체가 없다. 생성되면 여기 등재만 하면 된다.
  scada_record
      → RDS 에는 (turbine_id, recorded_at, power_output) 3컬럼뿐이다(V2__...sql).
        운영·이상 보고서는 wind_speed / is_stopped / expected_power_unit /
        expected_power_pooled 가 있어야 집계되므로 지금 붙이면 전부 깨진다.
        컬럼이 채워지면 등재하면서 turbine_id→turbine_code, recorded_at→timestamp 별칭만
        붙이면 된다(_query 의 SELECT 에 AS 로).
"""
import os
import threading
import time

import pandas as pd

from app.core.config import DATA_DIR, DATA_SOURCE, DB_URL

# 논리 테이블명 → (RDS 테이블명, 조회 컬럼). 컬럼을 명시로 고정한다 — "SELECT *" 로 두면
# DB 에 컬럼이 추가될 때 DataFrame 모양이 조용히 바뀌어 집계가 소리 없이 달라진다.
_RDS_TABLES = {
    "turbine": ("turbines", (
        "turbine_id", "wind_farm_id", "turbine_model_id", "turbine_code",
        "turbine_latitude", "turbine_longitude", "installation_date", "created_at",
    )),
    "blade": ("blades", (
        "blade_id", "turbine_id", "blade_tag", "installation_date", "created_at",
    )),
    "wind_farm": ("wind_farms", (
        "wind_farm_id", "wind_farm_name", "wind_farm_latitude", "wind_farm_longitude",
        "capacity", "installation_date", "address", "created_at",
        "aws_station_id", "asos_station_id",
    )),
    "turbine_model": ("turbine_models", (
        "turbine_model_id", "model", "manufacturer", "rated_power", "rotor_diameter",
        "hub_height", "blade_length", "cut_in_speed", "rated_speed", "cut_out_speed",
    )),
    # users 는 인증 컬럼(password·employee_id 등)까지 갖고 있다 — 보고서가 쓰는 것만 고른다.
    "user": ("users", ("user_id", "user_name", "role", "status")),

    # ── 결함 진단(defect) 체인 — V4 마이그레이션으로 생겼다 ──────────────────
    # report 는 DB 쪽이 CSV(3컬럼)보다 넓지만, 보고서가 읽는 컬럼만 고르면 CSV 와 같은 모양이 된다.
    # 나머지(wind_farm_id·period_*·content 등)는 조회에 쓰지 않으므로 SELECT 에서 뺀다.
    "report": ("report", ("report_id", "report_type", "status")),
    "inspection": ("inspection", (
        "inspection_id", "turbine_id", "user_id", "report_id",
        "inspection_start", "inspection_end", "status", "created_at",
    )),
    "defect": ("defect", (
        "defect_id", "inspection_id", "blade_id", "defect_type", "severity", "part_side",
        "bbox_x", "bbox_y", "bbox_w", "bbox_h", "confidence", "image_path", "created_at",
    )),
}

# 테이블별 날짜 컬럼. 호출부가 아니라 여기서 정해야 CSV/RDS 어느 쪽으로 읽어도 dtype 이 같고,
# 같은 테이블이 서로 다른 parse_dates 로 두 번 캐시되는 일도 없다.
_DATE_COLS = {
    "anomaly_event": ("start_time", "end_time", "created_at"),
    "aws_record": ("timestamp",),
    "blade": ("installation_date", "created_at"),
    "defect": ("created_at",),
    "inspection": ("inspection_start", "inspection_end", "created_at"),
    "scada_record": ("timestamp",),
    "turbine": ("installation_date", "created_at"),
    "wind_farm": ("installation_date", "created_at"),
}

USE_RDS = DATA_SOURCE.strip().lower() == "rds"

# 엄격 모드. DATA_SOURCE=rds 인데 _RDS_TABLES 에 없는 테이블을 읽으려 하면 조용히 CSV 로
# 떨어지지 않고 즉시 실패한다. "RDS 로 돌렸다"고 믿었는데 실은 CSV 였던 상황을 막는 장치다
# — 테이블명을 틀리게 등재하거나 스키마를 안 만든 채 시험하면 보고서는 멀쩡히 나오므로
# 눈으로는 절대 구별되지 않는다. 새 테이블을 RDS 로 옮길 때 이걸 켜고 한 번 돌려볼 것.
STRICT_RDS = USE_RDS and os.getenv("STRICT_RDS", "").strip().lower() == "true"

# 캐시 수명(초). CSV 는 파일이 안 바뀌므로 프로세스 내내 들고 있어도 되지만, RDS 는 살아있는
# 데이터다 — API 서버가 장수 프로세스라 무기한 캐시하면 첫 요청 때의 스냅샷을 계속 내보내게 되고,
# 새로 들어온 점검·결함이 보고서에 영원히 안 나타난다. 0 이면 무제한(만료 없음).
CACHE_TTL = float(os.getenv("DATA_CACHE_TTL", "300")) if USE_RDS else 0.0

_engine = None
_engine_lock = threading.Lock()
_cache = {}    # key -> (적재시각, 값)
_sources = {}  # 테이블명 -> "RDS" | "CSV" (sources() 로 조회)
# RLock 이어야 한다 — turbine_index() 가 락을 쥔 채 load_table() 을 부르므로
# 평범한 Lock 이면 같은 스레드가 자기 락을 기다리며 멈춘다.
_cache_lock = threading.RLock()


def is_rds(name: str) -> bool:
    """이 테이블이 실제로 RDS 에서 오는가. (DATA_SOURCE=rds 이면서 등재된 테이블만 True)"""
    return USE_RDS and name in _RDS_TABLES


def cached(key: str, build):
    """key 로 캐시된 값을 주거나, 없으면 build() 해서 캐시한다(CACHE_TTL 만료 적용).

    보고서 1건을 만드는 동안은 같은 스냅샷을 봐야 한다 — 표와 종합분석이 서로 다른 시점의
    데이터를 인용하면 critic 검증이 흔들린다. TTL 은 그 다음 보고서부터 새로 읽히게 하는 값이다.
    파생 프레임(조인 결과)도 이 함수를 거쳐야 원본과 같이 만료된다.
    """
    entry = _cache.get(key)
    if entry is not None and (CACHE_TTL <= 0 or time.monotonic() - entry[0] < CACHE_TTL):
        return entry[1]
    with _cache_lock:
        entry = _cache.get(key)
        if entry is None or (CACHE_TTL > 0 and time.monotonic() - entry[0] >= CACHE_TTL):
            _cache[key] = (time.monotonic(), build())
        return _cache[key][1]


def reset_cache():
    """캐시 전체 비우기. 데이터 적재 직후 강제 갱신이나 테스트에서 쓴다."""
    with _cache_lock:
        _cache.clear()
        _sources.clear()


def _get_engine():
    """SQLAlchemy 엔진(프로세스당 1개). RDS 를 실제로 읽는 순간에만 만든다."""
    global _engine
    if _engine is not None:
        return _engine
    with _engine_lock:
        if _engine is None:
            if not DB_URL:
                raise RuntimeError("DATA_SOURCE=rds 인데 DB_URL 이 비어 있다 (.env 확인).")
            try:
                from sqlalchemy import create_engine
            except ImportError as e:
                raise RuntimeError(
                    "DATA_SOURCE=rds 에는 sqlalchemy·pymysql 이 필요하다 (requirements.txt)."
                ) from e
            # pool_pre_ping: RDS 가 유휴 커넥션을 끊어도 다음 쿼리에서 조용히 재연결한다.
            # pool_recycle: wait_timeout(기본 8h) 보다 먼저 커넥션을 버려 끊긴 소켓 재사용을 막는다.
            _engine = create_engine(DB_URL, pool_pre_ping=True, pool_recycle=3600)
    return _engine


def _read_csv(name: str) -> pd.DataFrame:
    path = os.path.join(DATA_DIR, f"{name}.csv")
    return pd.read_csv(
        path,
        encoding="utf-8-sig",
        parse_dates=list(_DATE_COLS.get(name, ())) or None,
    )


def _query(name: str) -> pd.DataFrame:
    table, cols = _RDS_TABLES[name]
    # 식별자는 위 상수에서만 온다 — 외부 입력이 섞이지 않으므로 f-string 조립이 안전하다.
    df = pd.read_sql(f"SELECT {', '.join(cols)} FROM {table}", _get_engine())
    # DATE 를 date 객체로 돌려주는 드라이버가 있어 CSV 경로(datetime64)와 dtype 이 갈린다 — 통일.
    for c in _DATE_COLS.get(name, ()):
        if c in df.columns:
            df[c] = pd.to_datetime(df[c], errors="coerce")
    return df


def load_table(name: str) -> pd.DataFrame:
    """논리 테이블 1개 → DataFrame. 프로세스 수명 동안 1회만 읽는다.

    캐시된 객체를 그대로 돌려주므로 호출부는 in-place 로 수정하면 안 된다(.copy() 후 수정).
    기존 모듈들이 모듈 전역에 read_csv 결과를 담아두고 쓰던 것과 같은 계약이다.
    """
    rds = is_rds(name)
    # 캐시 적중 여부와 무관하게 기록한다 — '이 실행에서 이 테이블을 어디서 쓰는가'가 알고 싶은 것.
    _sources[name] = "RDS" if rds else "CSV"
    if STRICT_RDS and not rds:
        raise RuntimeError(
            f"STRICT_RDS: '{name}' 을 CSV 에서 읽으려 한다. "
            f"_RDS_TABLES 에 등재돼 있지 않다 (등재된 것: {sorted(_RDS_TABLES)})."
        )
    return cached(f"table:{name}", lambda: _query(name) if rds else _read_csv(name))


def sources() -> dict:
    """이번 실행에서 읽은 테이블 → "RDS" | "CSV". 보고서가 실제로 어디서 왔는지 확인용.

    등재 여부(is_rds)가 아니라 '실제로 읽었는지'를 담으므로, 보고서 종류별로 캐시를 비우고
    돌리면 그 보고서가 쓰는 테이블만 나온다. scripts/check_sources.py 가 이걸 쓴다.
    """
    return dict(_sources)


def turbine_index() -> pd.DataFrame:
    """터빈 참조표 — turbine_id, turbine_code, wind_farm_id, wind_farm_name.

    ERD 상 각 테이블은 코드·이름값 없이 FK 만 갖는다. 그 FK 를 사람이 읽는 값으로 푸는 조인은
    여기 한 곳에만 둔다(defect 의 점검·결함 집계와 operation 의 결함 건수가 같은 표를 쓴다).
    how="left" — wind_farm FK 가 깨져도 터빈 행을 잃지 않는다(이름만 빈다).
    """
    return cached("turbine_index", lambda: load_table("turbine")[
        ["turbine_id", "turbine_code", "wind_farm_id"]
    ].merge(
        load_table("wind_farm")[["wind_farm_id", "wind_farm_name"]],
        on="wind_farm_id",
        how="left",
    ))


def table_available(name: str) -> bool:
    """조회 가능한 테이블인가. RDS 등재분은 항상 True, 그 외는 CSV 파일 존재 여부."""
    if is_rds(name):
        return True
    return os.path.exists(os.path.join(DATA_DIR, f"{name}.csv"))
