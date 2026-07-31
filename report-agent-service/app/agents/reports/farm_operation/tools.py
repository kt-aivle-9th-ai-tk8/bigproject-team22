"""farm_operation(단지 운영) 데이터 조회 — LLM 절대 사용 안 함.

단지(발전소 전체) 운영 보고서: 전 터빈(U1~U8) 전 구간을 집계한다.
공유 계약 fetch(event_id)에서 event_id는 '단지 id'로 해석(현재 화순 단일 단지라 값은 무시,
scada에 데이터가 있으면 found=True). 기간은 scada 전 구간.

집계:
  scada  : 단지 총 발전량(MWh)·달성률·가동률·손실 분해·평균 풍속·월별 총량 + 터빈별 실적
  anomaly: 단지 전체 이상 이벤트(상태별/유형별)
  defect : 단지 전체 결함(데이터 없으면 미가용)
"""
import os
import pandas as pd

from app.core.config import DATA_DIR

_events = pd.read_csv(
    os.path.join(DATA_DIR, "anomaly_event.csv"),
    encoding="utf-8-sig", parse_dates=["start_time", "end_time"],
)
_scada = pd.read_csv(
    os.path.join(DATA_DIR, "scada_record.csv"),
    encoding="utf-8-sig", parse_dates=["timestamp"],
)

DEFAULT_FARM = "화순풍력발전소"
EVENT_CATEGORY = {
    "prolonged_stop": "stop", "data_missing": "data_missing",
    "chronic_screening": "degradation", "chronic_confirmed": "degradation",
}


def _num(v):
    if pd.isna(v):
        return None
    if isinstance(v, (pd.Timestamp,)):
        return v.isoformat(sep=" ")
    return v.item() if hasattr(v, "item") else v


def get_farm_scada() -> dict:
    """단지 전체 scada 집계 + 터빈별 실적 + 월별 총량."""
    df = _scada
    if df.empty:
        return {"found": False}

    # 손실 계산은 expected/actual이 모두 있는 행만 사용 — expected에 NaN이 있으면
    #   sum()은 NaN을 무시(0 취급)하지만 gap(뺄셈)은 NaN이 되어 총손실과 분해 합이 어긋난다.
    valid = df[["expected_power_unit", "power_output"]].notna().all(axis=1)
    dfv = df[valid]
    total_actual_kwh = dfv["power_output"].sum()
    total_expected_kwh = dfv["expected_power_unit"].sum()
    util = (total_actual_kwh / total_expected_kwh * 100) if total_expected_kwh else None
    availability = (1 - df["is_stopped"].mean()) * 100
    avg_wind = df["wind_speed"].mean()

    # 손실 분해: 두 항의 합 = 총손실(기대-실측)이 되도록 양쪽 모두 순합(clip 없음, 유효 행만).
    gap = dfv["expected_power_unit"] - dfv["power_output"]
    stopped = dfv["is_stopped"] == 1
    downtime_loss_kwh = gap[stopped].sum()
    perf_loss_kwh = gap[~stopped].sum()

    # 터빈별 실적 (달성률 오름차순 = 부진 터빈 먼저). 손실 기여 합 = 단지 총손실이 되도록 유효 행만.
    g = dfv.groupby("turbine_code").agg(
        actual_sum=("power_output", "sum"),
        expected_sum=("expected_power_unit", "sum"),
        stopped=("is_stopped", "mean"),
    )
    per_turbine = []
    for code, r in g.iterrows():
        exp = r["expected_sum"]
        per_turbine.append({
            "turbine_code": code,
            "actual_mwh": _num(r["actual_sum"] / 1000.0),
            "expected_mwh": _num(exp / 1000.0),
            "loss_mwh": _num((exp - r["actual_sum"]) / 1000.0),   # 손실 기여도
            "utilization_pct": _num((r["actual_sum"] / exp * 100) if exp else None),
            "availability_pct": _num((1 - r["stopped"]) * 100),
        })
    per_turbine.sort(key=lambda x: (x["utilization_pct"] is None, x["utilization_pct"]))

    # 월별 단지 총 발전량 (MWh)
    dfm = df.copy()
    dfm["month"] = dfm["timestamp"].dt.strftime("%Y-%m")
    grp = dfm.groupby("month").agg(expected=("expected_power_unit", "sum"),
                                   actual=("power_output", "sum"))
    monthly = [{"month": m, "expected": _num(r["expected"] / 1000.0),
                "actual": _num(r["actual"] / 1000.0)} for m, r in grp.iterrows()]

    return {
        "found": True,
        "n_turbines": int(df["turbine_code"].nunique()),
        "period_start": _num(df["timestamp"].min()),
        "period_end": _num(df["timestamp"].max()),
        "total_actual_mwh": _num(total_actual_kwh / 1000.0),
        "total_expected_mwh": _num(total_expected_kwh / 1000.0),
        "utilization_pct": _num(util),
        "availability_pct": _num(availability),
        "avg_wind_speed": _num(avg_wind),
        "energy_loss_mwh": _num((total_expected_kwh - total_actual_kwh) / 1000.0),
        "downtime_loss_mwh": _num(downtime_loss_kwh / 1000.0),
        "performance_loss_mwh": _num(perf_loss_kwh / 1000.0),
        "per_turbine": per_turbine,
        "monthly": monthly,
    }


def get_farm_anomaly(start, end_excl) -> dict:
    """단지 전체 이상 이벤트 집계 + 터빈별 분포."""
    df = _events[(_events["start_time"] >= start) & (_events["start_time"] < end_excl)].copy()
    cats = df["event_type"].map(EVENT_CATEGORY).fillna("other")
    cat_counts = cats.value_counts().to_dict()

    # 터빈별 이벤트 분포 (많은 순) — 이벤트가 어느 터빈에 몰렸나
    by_turbine = []
    if len(df):
        df["cat"] = cats.values
        for code, sub in df.groupby("turbine_code"):
            c = sub["cat"].value_counts().to_dict()
            by_turbine.append({
                "turbine_code": code, "total": int(len(sub)),
                "stop": int(c.get("stop", 0)),
                "data_missing": int(c.get("data_missing", 0)),
                "degradation": int(c.get("degradation", 0)),
            })
        by_turbine.sort(key=lambda x: -x["total"])

    return {
        "total": int(len(df)),
        "ongoing": int(df["end_time"].isna().sum()),
        "ended": int(df["end_time"].notna().sum()),
        "by_category": {
            "stop": int(cat_counts.get("stop", 0)),
            "data_missing": int(cat_counts.get("data_missing", 0)),
            "degradation": int(cat_counts.get("degradation", 0)),
            # 미지의 event_type도 노출 — total과 유형별 합 불일치 방지
            "other": int(cat_counts.get("other", 0)),
        },
        "by_turbine": by_turbine,
    }


def get_farm_defect(start, end_excl) -> dict:
    """단지 전체 결함 건수. 데이터 없으면 미가용."""
    dpath = os.path.join(DATA_DIR, "defect.csv")
    ipath = os.path.join(DATA_DIR, "inspection.csv")
    if not (os.path.exists(dpath) and os.path.exists(ipath)):
        return {"available": False, "count": 0}
    try:
        defects = pd.read_csv(dpath, encoding="utf-8-sig")
        insp = pd.read_csv(ipath, encoding="utf-8-sig", parse_dates=["inspection_start"])
        insp = insp[(insp["inspection_start"] >= start) & (insp["inspection_start"] < end_excl)]
        merged = defects[defects["inspection_id"].isin(insp["inspection_id"])]
        return {"available": True, "count": int(len(merged))}
    except Exception:
        return {"available": False, "count": 0}


def fetch(event_id: int) -> dict:
    """event_id(=단지 id, 단일 단지라 값 무시) → tool_outputs. 단지 전체 집계.

    'event' 키는 공유 service의 존재 여부(found) 계약용.
    """
    scada = get_farm_scada()
    if not scada.get("found"):
        return {"event": {"found": False}, "farm": {"found": False}}

    start = pd.to_datetime(scada["period_start"])
    end_excl = pd.to_datetime(scada["period_end"]) + pd.Timedelta(seconds=1)
    return {
        "event": {"found": True},
        "farm": {
            "found": True,
            "farm_name": DEFAULT_FARM,
            "period_start": start.strftime("%Y-%m-%d"),
            "period_end": pd.to_datetime(scada["period_end"]).strftime("%Y-%m-%d"),
            "n_turbines": scada["n_turbines"],
        },
        "scada": scada,
        "anomaly": get_farm_anomaly(start, end_excl),
        "defect": get_farm_defect(start, end_excl),
    }
