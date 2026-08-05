"""operation(운영) 데이터 조회 — LLM 절대 사용 안 함.

실제 ERD 스키마 기준(turbine_id FK). 공유 계약은 fetch(event_id: int) 하나이므로
event_id를 'turbine_id'로 해석한다: event_id=2 → turbine_id 2 (turbine.turbine_code로 표기).
기간은 해당 터빈 scada 전 구간.

집계:
  1) anomaly_event : 기간 내 유지중(end_time NULL)/종료 건수 + 유형별(정지/데이터부재/저하)
  2) scada_record  : power_output/expected_power_unit 평균·총량(MWh),
                     가동률(is_stopped)·손실 분해(정지/성능저하)·평균 풍속·월별 시계열
  3) inspection/defect : 기간 내 드론 점검 횟수, 신규 결함 수, 고위험 결함 수
                         (ERD Defect→Inspection→Turbine 조인)

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
    parse_dates=["recorded_at"],
)
_turbine = pd.read_csv(os.path.join(DATA_DIR, "turbine.csv"), encoding="utf-8-sig").dropna(
    subset=["turbine_id"]
)
_turbine["turbine_id"] = _turbine["turbine_id"].astype(int)
_farm = pd.read_csv(os.path.join(DATA_DIR, "wind_farm.csv"), encoding="utf-8-sig")

# 결함 계열(있으면 사용, 없으면 미가용 처리)
def _try_read(name, **kw):
    path = os.path.join(DATA_DIR, name)
    if not os.path.exists(path):
        return None
    try:
        return pd.read_csv(path, encoding="utf-8-sig", **kw)
    except Exception:
        return None


_inspection = _try_read("inspection.csv", parse_dates=["inspection_start", "inspection_end"])
_defect = _try_read("defect.csv")

KNOWN_TURBINE_IDS = sorted(_scada["turbine_id"].unique().tolist())

# 심각도 임계 — severity가 이 값 이상이면 '고위험'으로 집계 (ERD: 1~4 CNN 분류)
HIGH_SEVERITY = 3

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


def get_turbine_info(turbine_id: int) -> dict:
    """turbine + wind_farm 조인 → 터빈 코드·발전소명. 없으면 {'found': False}."""
    row = _turbine[_turbine["turbine_id"] == int(turbine_id)]
    if row.empty:
        return {"found": False}
    r = row.iloc[0]
    farm_name = None
    fr = _farm[_farm["wind_farm_id"] == r["wind_farm_id"]]
    if not fr.empty:
        farm_name = fr.iloc[0]["wind_farm_name"]
    return {
        "found": True,
        "turbine_id": int(turbine_id),
        "turbine_code": r["turbine_code"],
        "farm_name": farm_name or "발전소",
    }


def get_anomaly_summary(turbine_id, start, end_excl) -> dict:
    """기간 내 이상 이벤트 집계 — 상태별/유형별."""
    df = _events[
        (_events["turbine_id"] == int(turbine_id))
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
            # 미지의 event_type(EVENT_CATEGORY 미등록)도 노출 — total과 유형별 합 불일치 방지
            "other": int(cats.get("other", 0)),
        },
    }


def get_scada_summary(turbine_id) -> dict:
    """터빈 전 구간 scada 집계 + 운영 진단 지표 + 월별 시계열."""
    df = _scada[_scada["turbine_id"] == int(turbine_id)].copy()
    if df.empty:
        return {"found": False, "n_rows": 0}

    # 유효 행(expected/actual 모두 존재)만 사용 — 리포트의 모든 발전 지표가 같은 모집단을 쓰도록.
    #   expected에 NaN이 있으면 sum()은 무시(0 취급)하지만 gap(뺄셈)은 NaN이 되어
    #   총손실과 분해 합이 어긋나고, 평균·월별과도 모집단이 달라진다.
    valid = df[["expected_power_unit", "power_output"]].notna().all(axis=1)
    dfv = df[valid]

    avg_actual = dfv["power_output"].mean()
    avg_expected = dfv["expected_power_unit"].mean()
    util = (avg_actual / avg_expected * 100) if avg_expected else None

    # 총 발전량 (시간별 kW 합 = kWh → /1000 = MWh)
    total_actual_kwh = dfv["power_output"].sum()
    total_expected_kwh = dfv["expected_power_unit"].sum()

    # 운영 진단: 가동률 / 손실 분해(정지 vs 성능저하) / 풍황
    availability = (1 - dfv["is_stopped"].mean()) * 100
    avg_wind = dfv["wind_speed"].mean()
    # 손실 분해: 정지 구간 gap + 가동 구간 gap. 두 항의 합 = 총손실(기대-실측)이 되도록
    #   양쪽 모두 clip 없이 순합을 쓴다(가동 구간의 초과발전이 상쇄되어야 분해 합이 총손실과 일치).
    gap = dfv["expected_power_unit"] - dfv["power_output"]
    stopped = dfv["is_stopped"] == 1
    downtime_loss_kwh = gap[stopped].sum()
    perf_loss_kwh = gap[~stopped].sum()

    dfm = dfv.copy()   # 월별 추이도 같은 유효 행 모집단 사용
    dfm["month"] = dfm["recorded_at"].dt.strftime("%Y-%m")
    grp = dfm.groupby("month").agg(expected=("expected_power_unit", "mean"),
                                   actual=("power_output", "mean"))
    monthly = [{"month": m, "expected": _num(r["expected"]), "actual": _num(r["actual"])}
               for m, r in grp.iterrows()]

    return {
        "found": True,
        "n_rows": int(len(dfv)),
        "period_start": _num(df["recorded_at"].min()),
        "period_end": _num(df["recorded_at"].max()),
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


def get_defect_summary(turbine_id, start, end_excl) -> dict:
    """기간 내 드론 점검 횟수·신규 결함 수·고위험 결함 수 (Defect→Inspection→Turbine).

    RDS: SELECT ... FROM defect d JOIN inspection i ON d.inspection_id = i.inspection_id
         WHERE i.turbine_id = :tid AND i.inspection_start >= :start AND < :end
    """
    if _inspection is None or _defect is None:
        return {"available": False, "n_inspections": 0, "count": 0, "high_severity": 0}

    insp = _inspection[
        (_inspection["turbine_id"] == int(turbine_id))
        & (_inspection["inspection_start"] >= start)
        & (_inspection["inspection_start"] < end_excl)
    ]
    d = _defect[_defect["inspection_id"].isin(insp["inspection_id"])]
    high = int((d["severity"] >= HIGH_SEVERITY).sum()) if "severity" in d.columns else 0
    types = d["defect_type"].value_counts().to_dict() if "defect_type" in d.columns else {}
    return {
        "available": True,
        "n_inspections": int(len(insp)),      # 드론 점검 횟수
        "count": int(len(d)),                  # 신규 발견 결함 수
        "high_severity": high,                 # 고위험 결함 수(severity >= 3)
        "by_type": {str(k): int(v) for k, v in list(types.items())[:5]},
    }


def fetch(event_id: int) -> dict:
    """event_id(=turbine_id) → tool_outputs. 이상 이벤트·발전 실적·결함 집계.

    'event' 키는 공유 service가 존재 여부(found)를 읽는 계약이라 함께 노출한다.
    """
    info = get_turbine_info(event_id)
    if not info.get("found") or int(event_id) not in KNOWN_TURBINE_IDS:
        return {"event": {"found": False}, "turbine": {"found": False, "turbine_id": event_id}}

    scada = get_scada_summary(event_id)
    start = pd.to_datetime(scada["period_start"])
    end_excl = pd.to_datetime(scada["period_end"]) + pd.Timedelta(seconds=1)

    return {
        "event": {"found": True},   # service의 generic found-체크용
        "turbine": {
            "found": True,
            "turbine_id": info["turbine_id"],
            "turbine_code": info["turbine_code"],
            "farm_name": info["farm_name"],
            "period_start": start.strftime("%Y-%m-%d"),
            "period_end": pd.to_datetime(scada["period_end"]).strftime("%Y-%m-%d"),
        },
        "anomaly": get_anomaly_summary(event_id, start, end_excl),
        "scada": scada,
        "defect": get_defect_summary(event_id, start, end_excl),
    }
