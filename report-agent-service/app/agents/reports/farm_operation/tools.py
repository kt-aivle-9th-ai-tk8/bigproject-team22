"""farm_operation(단지 운영) 데이터 조회 — LLM 절대 사용 안 함.

실제 ERD 스키마 기준. 공유 계약 fetch(event_id, params)에서 event_id는 'wind_farm_id'로 해석한다
(예: 4 = 화순 풍력 발전소). 해당 단지 소속 터빈 전체를 집계하며, 기간은 params(선택, 없으면 전 구간).

집계:
  scada      : 단지 총 발전량(MWh)·달성률·가동률·손실 분해·평균 풍속·월별 총량 + 터빈별 실적
  anomaly    : 단지 전체 이상 이벤트(상태별/유형별) + 터빈별 분포
  inspection/defect : 드론 점검 횟수, 신규 결함 수, 고위험 결함 수

조회는 app.core.datasource 의 범위 조회(query)를 쓴다 — 단지 소속 터빈·기간으로 좁혀 읽는다.
operation.tools 의 공용 상수·헬퍼는 그대로 재사용한다.
"""
import pandas as pd

from app.agents.reports.operation.tools import (
    _turbine, _farm, EVENT_CATEGORY, HIGH_SEVERITY, _num, _period_args,
)
from app.core.datasource import query, table_available


def _scada_of(turbine_ids, start=None, end_excl=None):
    """단지 소속 터빈들의 scada — 기간이 주어지면 그 범위만."""
    return query("scada_record",
                 isin={"turbine_id": [int(t) for t in turbine_ids]},
                 span={"recorded_at": (start, end_excl)} if (start or end_excl) else None)


def _events_of(turbine_ids, start=None, end_excl=None):
    """단지 소속 터빈들의 이상 이벤트 — start_time 기준 기간 필터."""
    return query("anomaly_event",
                 isin={"turbine_id": [int(t) for t in turbine_ids]},
                 span={"start_time": (start, end_excl)} if (start or end_excl) else None)


def get_farm_info(wind_farm_id: int) -> dict:
    """wind_farm + 소속 터빈 목록. 없으면 {'found': False}."""
    wf = _farm()
    fr = wf[wf["wind_farm_id"] == int(wind_farm_id)]
    if fr.empty:
        return {"found": False}
    tb = _turbine()
    turbines = tb[tb["wind_farm_id"] == int(wind_farm_id)]
    ids = sorted(turbines["turbine_id"].astype(int).tolist())
    code_map = dict(zip(turbines["turbine_id"].astype(int), turbines["turbine_code"]))
    return {
        "found": True,
        "wind_farm_id": int(wind_farm_id),
        "farm_name": fr.iloc[0]["wind_farm_name"],
        "turbine_ids": ids,
        "code_map": code_map,
    }


def get_farm_scada(turbine_ids, code_map, period_start=None, period_end=None) -> dict:
    """단지 전체 scada 집계 + 터빈별 실적 + 월별 총량. 기간 미지정 시 관측 전 구간."""
    start, end_excl = _period_args(period_start, period_end)
    df = _scada_of(turbine_ids, start, end_excl)
    if df.empty:
        reason = "해당 기간 관측 데이터 없음" if (start or end_excl) else "관측 데이터 없음"
        return {"found": False, "reason": reason}

    # 집계 경계 — 기간을 안 줬으면 실제 관측 구간이 곧 요청 구간이다.
    if start is None:
        start = df["recorded_at"].min()
    if end_excl is None:
        end_excl = df["recorded_at"].max() + pd.Timedelta(seconds=1)

    # 유효 행(expected/actual 모두 존재)만 사용 — 모든 발전 지표가 같은 모집단을 쓰도록.
    valid = df[["expected_power_unit", "power_output"]].notna().all(axis=1)
    dfv = df[valid]

    total_actual_kwh = dfv["power_output"].sum()
    total_expected_kwh = dfv["expected_power_unit"].sum()
    util = (total_actual_kwh / total_expected_kwh * 100) if total_expected_kwh else None
    availability = (1 - dfv["is_stopped"].mean()) * 100
    avg_wind = dfv["wind_speed"].mean()

    # 손실 분해: 두 항의 합 = 총손실(기대-실측)이 되도록 양쪽 모두 순합(clip 없음).
    gap = dfv["expected_power_unit"] - dfv["power_output"]
    stopped = dfv["is_stopped"] == 1
    downtime_loss_kwh = gap[stopped].sum()
    perf_loss_kwh = gap[~stopped].sum()

    # 터빈별 실적 (손실 기여 합 = 단지 총손실이 되도록 유효 행만)
    g = dfv.groupby("turbine_id").agg(
        actual_sum=("power_output", "sum"),
        expected_sum=("expected_power_unit", "sum"),
        stopped=("is_stopped", "mean"),
    )
    per_turbine = []
    for tid, r in g.iterrows():
        exp = r["expected_sum"]
        per_turbine.append({
            "turbine_id": int(tid),
            "turbine_code": code_map.get(int(tid), f"ID{int(tid)}"),
            "actual_mwh": _num(r["actual_sum"] / 1000.0),
            "expected_mwh": _num(exp / 1000.0),
            "loss_mwh": _num((exp - r["actual_sum"]) / 1000.0),   # 손실 기여도
            "utilization_pct": _num((r["actual_sum"] / exp * 100) if exp else None),
            "availability_pct": _num((1 - r["stopped"]) * 100),
        })
    per_turbine.sort(key=lambda x: (x["utilization_pct"] is None, x["utilization_pct"]))

    # 월별 단지 총 발전량 (MWh)
    dfm = dfv.copy()
    dfm["month"] = dfm["recorded_at"].dt.strftime("%Y-%m")
    grp = dfm.groupby("month").agg(expected=("expected_power_unit", "sum"),
                                   actual=("power_output", "sum"))
    monthly = [{"month": m, "expected": _num(r["expected"] / 1000.0),
                "actual": _num(r["actual"] / 1000.0)} for m, r in grp.iterrows()]

    return {
        "found": True,
        "n_turbines": int(dfv["turbine_id"].nunique()),
        "period_start": _num(df["recorded_at"].min()),
        "period_end": _num(df["recorded_at"].max()),
        # 요청 기간 경계(_period_bounds 결과) — anomaly·defect 를 요청 기간으로 집계하도록 fetch 로 넘긴다.
        "query_start": _num(start),
        "query_end_excl": _num(end_excl),
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


def get_farm_anomaly(turbine_ids, code_map, start, end_excl) -> dict:
    """단지 전체 이상 이벤트 집계 + 터빈별 분포."""
    df = _events_of(turbine_ids, start, end_excl).copy()
    cats = df["event_type"].map(EVENT_CATEGORY).fillna("other")
    cat_counts = cats.value_counts().to_dict()

    # 터빈별 이벤트 분포 (많은 순) — 이벤트가 어느 터빈에 몰렸나
    by_turbine = []
    if len(df):
        df["cat"] = cats.values
        for tid, sub in df.groupby("turbine_id"):
            c = sub["cat"].value_counts().to_dict()
            by_turbine.append({
                "turbine_code": code_map.get(int(tid), f"ID{int(tid)}"),
                "total": int(len(sub)),
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


def get_farm_defect(turbine_ids, start, end_excl) -> dict:
    """단지 전체 드론 점검 횟수·신규 결함 수·고위험 결함 수."""
    if not (table_available("inspection") and table_available("defect")):
        return {"available": False, "n_inspections": 0, "count": 0, "high_severity": 0}

    insp = query("inspection",
                 isin={"turbine_id": [int(t) for t in turbine_ids]},
                 span={"inspection_start": (start, end_excl)})
    d = query("defect", isin={"inspection_id": insp["inspection_id"].tolist()})
    high = int((d["severity"] >= HIGH_SEVERITY).sum()) if "severity" in d.columns else 0
    types = d["defect_type"].value_counts().to_dict() if "defect_type" in d.columns else {}
    return {
        "available": True,
        "n_inspections": int(len(insp)),
        "count": int(len(d)),
        "high_severity": high,
        "by_type": {str(k): int(v) for k, v in list(types.items())[:5]},
    }


def fetch(event_id: int, params: dict = None) -> dict:
    """event_id(=wind_farm_id) + params(기간) → tool_outputs. 단지 전체 집계.

    params: {"period_start": "YYYY-MM-DD", "period_end": "YYYY-MM-DD"} (선택).
      미지정 시 해당 단지의 관측 전 구간. period_end는 그 날짜 끝까지 포함.

    'event' 키는 공유 service의 존재 여부(found) 계약용.
    """
    p = params or {}
    info = get_farm_info(event_id)
    if not info.get("found") or not info["turbine_ids"]:
        return {"event": {"found": False}, "farm": {"found": False, "wind_farm_id": event_id}}

    ids, code_map = info["turbine_ids"], info["code_map"]
    scada = get_farm_scada(ids, code_map, p.get("period_start"), p.get("period_end"))
    if not scada.get("found"):
        return {"event": {"found": False}, "farm": {"found": False, "wind_farm_id": event_id}}

    # 표시 기간 = 실제 관측 데이터 범위 / 집계 경계 = 요청 기간(미지정 시 동일).
    disp_start = pd.to_datetime(scada["period_start"])
    disp_end = pd.to_datetime(scada["period_end"])
    q_start = pd.to_datetime(scada["query_start"])
    q_end_excl = pd.to_datetime(scada["query_end_excl"])
    return {
        "event": {"found": True},
        "farm": {
            "found": True,
            "wind_farm_id": info["wind_farm_id"],
            "farm_name": info["farm_name"],
            "period_start": disp_start.strftime("%Y-%m-%d"),
            "period_end": disp_end.strftime("%Y-%m-%d"),
            "n_turbines": scada["n_turbines"],
        },
        "scada": scada,
        "anomaly": get_farm_anomaly(ids, code_map, q_start, q_end_excl),
        "defect": get_farm_defect(ids, q_start, q_end_excl),
    }
