"""RDS 조회/저장 + LightGBM 예측 실행 어댑터.

detection/ 패키지(tier_a_rules.py 등)와 lightgbm/ 패키지(predict.py 등)는 각각
팀원 원본 그대로다. 이 파일이 실제 DB 스키마(turbine_id 중심, 영문 컬럼명)와
그 두 패키지가 기대하는 형태(발전호기=정수 unit, 한글 컬럼명, farm_code 문자열)
사이를 잇는다.

DB 스키마 기준 (2026-08-18 확인):
  scada_record : turbine_id, recorded_at, power_output, wind_speed, air_density,
                 norm_wind_speed, is_stopped, train_mask,
                 expected_power_pooled, expected_power_unit
  anomaly_event: event_id, turbine_id, start_time, end_time, expected_power,
                 actual_power, z_score, deviation_pct, tier, event_type, scope,
                 energy_ratio_30d, estimated_loss_kwh, created_at
                 ※ duration_hours / priority_rank 컬럼 없음
  aws_record   : aws_station_id, recorded_at, temperature, pressure, humidity, ...
  wind_farm    : wind_farm_id, wind_farm_name, aws_station_id, ...
  turbine      : turbine_id, wind_farm_id, turbine_code(순수 숫자 문자열 "1","2",...), ...
"""
import os

import pandas as pd
from sqlalchemy import create_engine, text

from lightgbm.predict import FarmModels, expected_power
from lightgbm.flags import FARM_SPEC, is_stopped as flag_is_stopped

DB_URL = os.environ["DB_URL"]  # mysql+pymysql://user:pass@host:port/schema

# wind_farm 테이블 실제 값 기준 (2026-08-18 확인)
# 단지 3(보성), 4(장흥호)는 FARM_SPEC에 없어 처리 대상에서 제외
FARM_CODE_BY_WIND_FARM_ID = {
    1: "jangheung",
    2: "hwasun",
}

_engine = None


def _get_engine():
    global _engine
    if _engine is None:
        _engine = create_engine(DB_URL, pool_pre_ping=True, pool_recycle=1800)
    return _engine


def _turbine_code_to_unit(turbine_code: str) -> int:
    """'1' → 1. turbine.turbine_code는 순수 숫자 문자열."""
    return int(turbine_code)


# ────────────────────────────────────────────────────────────────
# 1단계: 미예측 scada_record에 LightGBM 예측 + is_stopped 채우기
# ────────────────────────────────────────────────────────────────

def load_pending_predictions(farm_code: str) -> pd.DataFrame:
    """expected_power_pooled가 NULL인 scada_record 조회.
    기상(temperature/pressure/humidity)은 aws_record를
    wind_farm.aws_station_id 경로로 조인해 가져온다.
    """
    wind_farm_id = _farm_id(farm_code)
    sql = text("""
        SELECT
            sr.turbine_id, sr.recorded_at, sr.wind_speed, sr.power_output,
            t.turbine_code,
            ar.temperature, ar.pressure, ar.humidity
        FROM scada_record sr
        JOIN turbine t ON t.turbine_id = sr.turbine_id
        JOIN wind_farm wf ON wf.wind_farm_id = t.wind_farm_id
        LEFT JOIN aws_record ar
            ON ar.aws_station_id = wf.aws_station_id
           AND ar.recorded_at = sr.recorded_at
        WHERE sr.expected_power_pooled IS NULL
          AND t.wind_farm_id = :wind_farm_id
        ORDER BY sr.turbine_id, sr.recorded_at
    """)
    with _get_engine().connect() as conn:
        return pd.read_sql(sql, conn, params={"wind_farm_id": wind_farm_id})


def run_predictions(farm_code: str, models: FarmModels) -> int:
    """미예측 행을 찾아 LightGBM 예측 + is_stopped 계산 후 UPDATE. 처리 건수 반환."""
    pending = load_pending_predictions(farm_code)
    if pending.empty:
        return 0

    cut_in_ms = FARM_SPEC[farm_code]["cut_in_ms"]
    update_sql = text("""
        UPDATE scada_record
        SET expected_power_pooled = :pooled,
            expected_power_unit   = :unit,
            norm_wind_speed       = :norm_wind_speed,
            is_stopped            = :is_stopped
        WHERE turbine_id = :turbine_id
          AND recorded_at = :recorded_at
    """)

    with _get_engine().begin() as conn:
        for _, row in pending.iterrows():
            result = expected_power(
                models,
                turbine_code=row["turbine_code"],
                wind_speed=row["wind_speed"],
                temperature=row["temperature"],
                pressure=row["pressure"],
                humidity=row["humidity"],
            )
            stopped = flag_is_stopped(row["wind_speed"], row["power_output"], cut_in_ms)
            conn.execute(update_sql, {
                "pooled":          result["pooled"],
                "unit":            result["unit"],
                "norm_wind_speed": result["norm_wind_speed"],
                "is_stopped":      stopped,
                "turbine_id":      row["turbine_id"],
                "recorded_at":     row["recorded_at"],
            })

    return len(pending)


# ────────────────────────────────────────────────────────────────
# 2단계: detection/ 패키지 입력 형태로 scada_record 조회
# ────────────────────────────────────────────────────────────────

def load_scada_for_detection(farm_code: str, since=None) -> pd.DataFrame:
    """detection/(tier_a_rules.py, tier_b_batch.py)가 기대하는 컬럼명으로 변환하여 반환."""
    wind_farm_id = _farm_id(farm_code)
    since_clause = "AND sr.recorded_at >= :since" if since else ""
    sql = text(f"""
        SELECT
            sr.recorded_at, t.turbine_code, sr.power_output,
            sr.is_stopped, sr.expected_power_pooled, sr.expected_power_unit
        FROM scada_record sr
        JOIN turbine t ON t.turbine_id = sr.turbine_id
        WHERE t.wind_farm_id = :wind_farm_id
          {since_clause}
        ORDER BY t.turbine_code, sr.recorded_at
    """)
    params = {"wind_farm_id": wind_farm_id}
    if since:
        params["since"] = since

    with _get_engine().connect() as conn:
        df = pd.read_sql(sql, conn, params=params)

    # detection/ 패키지가 기대하는 컬럼명으로 rename
    df["일자"]                       = pd.to_datetime(df["recorded_at"])
    df["발전호기"]                    = df["turbine_code"].apply(_turbine_code_to_unit)
    df["계통 유효 전력(킬로와트)"]   = df["power_output"]
    df["flag_highwind_neg"]           = df["is_stopped"]
    return df[[
        "일자", "발전호기", "계통 유효 전력(킬로와트)",
        "flag_highwind_neg", "expected_power_pooled", "expected_power_unit",
    ]]


# ────────────────────────────────────────────────────────────────
# 3단계: 판정 결과 저장
# ────────────────────────────────────────────────────────────────

def save_anomaly_events(events: pd.DataFrame, farm_code: str) -> int:
    """이벤트 DataFrame을 anomaly_event 테이블에 저장.

    UNIQUE 제약이 없으므로 (turbine_id, tier, event_type, start_time) 기준으로
    기존 레코드를 확인한 뒤 없으면 INSERT, 있으면 UPDATE(end_time 등 변동 가능 컬럼만).

    ※ anomaly_event 테이블에 duration_hours / priority_rank 컬럼 없음 — 저장 제외.
    """
    if events.empty:
        return 0

    wind_farm_id = _farm_id(farm_code)
    with _get_engine().begin() as conn:
        turbine_map = dict(conn.execute(
            text("SELECT turbine_code, turbine_id FROM turbine WHERE wind_farm_id = :w"),
            {"w": wind_farm_id},
        ).all())

        check_sql = text("""
            SELECT event_id FROM anomaly_event
            WHERE turbine_id = :turbine_id
              AND tier        = :tier
              AND event_type  = :event_type
              AND start_time  = :start_time
            LIMIT 1
        """)
        insert_sql = text("""
            INSERT INTO anomaly_event
                (turbine_id, tier, event_type, start_time, end_time,
                 z_score, expected_power, actual_power, deviation_pct,
                 energy_ratio_30d, estimated_loss_kwh, scope)
            VALUES
                (:turbine_id, :tier, :event_type, :start_time, :end_time,
                 :z_score, :expected_power, :actual_power, :deviation_pct,
                 :energy_ratio_30d, :estimated_loss_kwh, :scope)
        """)
        update_sql = text("""
            UPDATE anomaly_event
            SET end_time            = :end_time,
                estimated_loss_kwh  = :estimated_loss_kwh
            WHERE event_id = :event_id
        """)

        n = 0
        for _, row in events.iterrows():
            turbine_id = turbine_map.get(str(row["turbine_code"]))
            if turbine_id is None:
                continue

            params = {
                "turbine_id":        turbine_id,
                "tier":              row["tier"],
                "event_type":        row["event_type"],
                "start_time":        row["start_time"],
                "end_time":          None if pd.isna(row["end_time"]) else row["end_time"],
                "z_score":           None if pd.isna(row.get("z_score", float("nan"))) else row.get("z_score"),
                "expected_power":    row.get("expected_power"),
                "actual_power":      row.get("actual_power"),
                "deviation_pct":     row.get("deviation_pct"),
                "energy_ratio_30d":  row.get("energy_ratio_30d"),
                "estimated_loss_kwh": row.get("estimated_loss_kwh"),
                "scope":             row.get("scope"),
            }

            existing = conn.execute(check_sql, {
                "turbine_id": turbine_id,
                "tier":       row["tier"],
                "event_type": row["event_type"],
                "start_time": row["start_time"],
            }).fetchone()

            if existing is None:
                conn.execute(insert_sql, params)
            else:
                conn.execute(update_sql, {
                    "event_id":          existing[0],
                    "end_time":          params["end_time"],
                    "estimated_loss_kwh": params["estimated_loss_kwh"],
                })
            n += 1

    return n


# ────────────────────────────────────────────────────────────────
# 내부 유틸
# ────────────────────────────────────────────────────────────────

def _farm_id(farm_code: str) -> int:
    """farm_code → wind_farm_id. FARM_CODE_BY_WIND_FARM_ID의 역매핑."""
    for fid, fc in FARM_CODE_BY_WIND_FARM_ID.items():
        if fc == farm_code:
            return fid
    raise ValueError(f"지원하지 않는 farm_code: {farm_code}. FARM_SPEC에 없는 단지입니다.")