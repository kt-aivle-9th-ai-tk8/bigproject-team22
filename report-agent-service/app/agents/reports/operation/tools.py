"""operation(운영) 데이터 조회 — LLM 절대 사용 안 함.

터빈별 운영 보고서는 '터빈 + 기간' 단위지만, 공유 계약은 fetch(event_id: int) 하나다.
그래서 event_id를 '터빈 번호'로 해석한다: event_id=2 → 터빈 U2, 기간=해당 터빈 scada 전 구간.
(입력 일반화(터빈+기간 params)는 shared State 변경이라 팀 합의 후 별도 진행.)

집계(요구사항 + 고도화):
  1) anomaly_event : 기간 내 유지중(end_time NULL)/종료 건수 + 유형별(정지/데이터부재/저하)
  2) scada_record  : power_output/expected_power_unit 평균·총량(MWh),
                     가동률(is_stopped)·손실 분해(정지/성능저하)·평균 풍속·월별 시계열
  3) defect        : 기간 내 결함 건수 (ERD Defect→Inspection→Turbine, 데이터 없으면 미가용)

반환 dict가 그대로 state['tool_outputs'] = critic 검증 기준. 원본 수치 그대로(가공 최소).
배포(RDS): DATA_SOURCE=="rds"면 조회부만 SELECT로 교체(시그니처·반환 dict 유지).
"""
import os
import pandas as pd

from app.core.config import DATA_DIR

# ── CSV 로드 (모듈 로드 시 1회) ────────────────────────────────────────────
_events = pd.read_csv(
    os.path.join(DATA_DIR, "anomaly_event.csv"),
    encoding="utf-8-sig",
    parse_dates=["start_time", "end_time"],
)
_scada = pd.read_csv(
    os.path.join(DATA_DIR, "scada_record.csv"),
    encoding="utf-8-sig",
    parse_dates=["timestamp"],
)

KNOWN_TURBINES = sorted(_scada["turbine_code"].unique().tolist())
DEFAULT_FARM = "화순풍력발전소"   # 데이터는 화순 U1~U8 (ERD WindFarm.wind_farm_name 대응)

# event_type → 운영 보고서용 3개 범주 (정지 / 데이터 부재 / 저하)
EVENT_CATEGORY = {
    "prolonged_stop": "stop",
    "data_missing": "data_missing",
    "chronic_screening": "degradation",
    "chronic_confirmed": "degradation",
}


def _num(v):
    """NaN/빈값 → None, Timestamp → ISO 문자열, 그 외 원본 파이썬 스칼라로."""
    if pd.isna(v):
        return None
    if isinstance(v, (pd.Timestamp,)):
        return v.isoformat(sep=" ")
    return v.item() if hasattr(v, "item") else v


def turbine_code_for(event_id: int) -> str:
    """event_id(정수) → 터빈 코드. 규칙: N → 'U{N}'."""
    return f"U{int(event_id)}"


def get_anomaly_summary(turbine_code, start, end_excl) -> dict:
    """기간 내 이상 이벤트 집계 — 상태별/유형별."""
    df = _events[
        (_events["turbine_code"] == turbine_code)
        & (_events["start_time"] >= start)
        & (_events["start_time"] < end_excl)
    ]
    cats = df["event_type"].map(EVENT_CATEGORY).fillna("other").value_counts().to_dict()
    return {
        "total": int(len(df)),
        "ongoing": int(df["end_time"].isna().sum()),   # 유지 중 (end_time NULL)
        "ended": int(df["end_time"].notna().sum()),     # 종료 (end_time 존재)
        "by_category": {
            "stop": int(cats.get("stop", 0)),
            "data_missing": int(cats.get("data_missing", 0)),
            "degradation": int(cats.get("degradation", 0)),
        },
    }


def get_scada_summary(turbine_code) -> dict:
    """터빈 전 구간 scada 집계 + 운영 진단 지표 + 월별 시계열."""
    df = _scada[_scada["turbine_code"] == turbine_code].copy()
    if df.empty:
        return {"found": False, "n_rows": 0}

    avg_actual = df["power_output"].mean()
    avg_expected = df["expected_power_unit"].mean()
    util = (avg_actual / avg_expected * 100) if avg_expected else None

    # 총 발전량 (시간별 kW 합 = kWh → /1000 = MWh)
    total_actual_kwh = df["power_output"].sum()
    total_expected_kwh = df["expected_power_unit"].sum()

    # 운영 진단: 가동률 / 손실 분해(정지 vs 성능저하) / 풍황
    availability = (1 - df["is_stopped"].mean()) * 100
    avg_wind = df["wind_speed"].mean()
    gap = df["expected_power_unit"] - df["power_output"]
    stopped = df["is_stopped"] == 1
    downtime_loss_kwh = gap[stopped].sum()
    perf_loss_kwh = gap[~stopped].clip(lower=0).sum()

    df["month"] = df["timestamp"].dt.strftime("%Y-%m")
    grp = df.groupby("month").agg(expected=("expected_power_unit", "mean"),
                                  actual=("power_output", "mean"))
    monthly = [{"month": m, "expected": _num(r["expected"]), "actual": _num(r["actual"])}
               for m, r in grp.iterrows()]

    return {
        "found": True,
        "n_rows": int(len(df)),
        "period_start": _num(df["timestamp"].min()),
        "period_end": _num(df["timestamp"].max()),
        "avg_actual_power": _num(avg_actual),
        "avg_expected_power": _num(avg_expected),
        "utilization_pct": _num(util),
        "total_actual_mwh": _num(total_actual_kwh / 1000.0),
        "total_expected_mwh": _num(total_expected_kwh / 1000.0),
        "availability_pct": _num(availability),
        "avg_wind_speed": _num(avg_wind),
        "energy_loss_mwh": _num((total_expected_kwh - total_actual_kwh) / 1000.0),
        "downtime_loss_mwh": _num(downtime_loss_kwh / 1000.0),
        "performance_loss_mwh": _num(perf_loss_kwh / 1000.0),
        "monthly": monthly,
    }


def get_defect_count(turbine_code, start, end_excl) -> dict:
    """기간 내 결함 건수. 데이터 없으면 {'available': False, 'count': 0}.

    RDS: SELECT COUNT(*) FROM defect d JOIN inspection i ON d.inspection_id=i.inspection_id
         JOIN turbine t ON i.turbine_id=t.turbine_id
         WHERE t.turbine_code=:code AND i.inspection_start >= :start AND < :end
    """
    dpath = os.path.join(DATA_DIR, "defect.csv")
    ipath = os.path.join(DATA_DIR, "inspection.csv")
    if not (os.path.exists(dpath) and os.path.exists(ipath)):
        return {"available": False, "count": 0}
    try:
        defects = pd.read_csv(dpath, encoding="utf-8-sig")
        insp = pd.read_csv(ipath, encoding="utf-8-sig", parse_dates=["inspection_start"])
        insp = insp[(insp["turbine_code"] == turbine_code)
                    & (insp["inspection_start"] >= start)
                    & (insp["inspection_start"] < end_excl)]
        merged = defects[defects["inspection_id"].isin(insp["inspection_id"])]
        return {"available": True, "count": int(len(merged))}
    except Exception:
        return {"available": False, "count": 0}


def fetch(event_id: int) -> dict:
    """event_id(=터빈 번호) → tool_outputs. 이상 이벤트·발전 실적·결함 집계.

    'event' 키는 공유 service가 존재 여부(found)를 읽는 계약이라 함께 노출한다.
    """
    code = turbine_code_for(event_id)
    if code not in KNOWN_TURBINES:
        return {"event": {"found": False}, "turbine": {"found": False, "turbine_code": code}}

    scada = get_scada_summary(code)
    start = pd.to_datetime(scada["period_start"])
    end_excl = pd.to_datetime(scada["period_end"]) + pd.Timedelta(seconds=1)
    start_str = start.strftime("%Y-%m-%d")
    end_str = pd.to_datetime(scada["period_end"]).strftime("%Y-%m-%d")

    return {
        "event": {"found": True},   # service의 generic found-체크용
        "turbine": {
            "found": True,
            "farm_name": DEFAULT_FARM,
            "turbine_code": code,
            "period_start": start_str,
            "period_end": end_str,
        },
        "anomaly": get_anomaly_summary(code, start, end_excl),
        "scada": scada,
        "defect": get_defect_count(code, start, end_excl),
    }
