"""데이터 조회 — LLM 절대 사용 안 함. 반환 수치는 원본 그대로(가공 금지).

소스 선택(CSV/RDS)은 app.core.datasource 가 테이블 단위로 하므로 이 파일은 그대로 둔다.
tool_outputs = builder(표·차트) + facts(종합분석 인용 수치) 양쪽의 단일 출처.
"""
import pandas as pd

from app.core.config import RECENT_HISTORY_MONTHS
from app.core.datasource import load_table

WINDOW_HOURS = {"prolonged_stop": 24, "data_missing": 6, "chronic_screening": 720, "chronic_confirmed": 720}
_CHRONIC = ("chronic_screening", "chronic_confirmed")


# 호출 시점 로드(datasource 가 1회 캐시). registry 가 4개 tools 를 전부 import 하므로
# 전역 로드로 두면 다른 보고서 1건에도 이상탐지용 테이블까지 다 읽게 된다.
def _events():
    return load_table("anomaly_event")


def _scada():
    return load_table("scada_record")


def _weather():
    return load_table("aws_record")


def _farm_turbines() -> list:
    return sorted(_scada()["turbine_code"].unique().tolist())


def _num(v):
    if pd.isna(v):
        return None
    if isinstance(v, pd.Timestamp):
        return v.isoformat(sep=" ")
    return v.item() if hasattr(v, "item") else v


def _observation_window(start, event_type):
    """만성은 [start-30일, start], 급성은 [start, start+H]."""
    delta = pd.Timedelta(hours=WINDOW_HOURS.get(event_type, 24))
    return (start - delta, start) if event_type in _CHRONIC else (start, start + delta)


def get_anomaly_event(event_id: int) -> dict:
    ev = _events()
    row = ev[ev["event_id"] == event_id]
    if row.empty:
        return {"found": False}
    r = row.iloc[0]
    end = _num(r["end_time"])
    return {
        "found": True, "event_id": int(r["event_id"]), "turbine_code": r["turbine_code"],
        "tier": r["tier"], "event_type": r["event_type"], "scope": _num(r["scope"]),
        "start_time": _num(r["start_time"]), "end_time": end, "ongoing": end is None,
        "expected_power": _num(r["expected_power"]), "actual_power": _num(r["actual_power"]),
        "z_score": _num(r["z_score"]), "deviation_pct": _num(r["deviation_pct"]),
        "energy_ratio_30d": _num(r["energy_ratio_30d"]), "estimated_loss_kwh": _num(r["estimated_loss_kwh"]),
    }


def get_scada_during_event(turbine_code, start_time, event_type) -> dict:
    """관측창 scada 집계(표·종합분석 공용). 평균·최대 풍속, 기대/실측 누적."""
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)
    sc = _scada()
    df = sc[(sc["turbine_code"] == turbine_code) & (sc["timestamp"] >= win_start)
            & (sc["timestamp"] < win_end)]
    if df.empty:
        return {"found": False, "n_rows": 0}
    return {
        "found": True, "n_rows": len(df),
        "avg_wind_speed": _num(df["wind_speed"].mean()), "max_wind_speed": _num(df["wind_speed"].max()),
        "avg_power": _num(df["power_output"].mean()),
        "sum_expected_power_pooled": _num(df["expected_power_pooled"].sum()),
        "sum_actual_power": _num(df["power_output"].sum()),
    }


def get_other_turbines(turbine_code, start_time, event_type) -> dict:
    """관측창 동안 '타 호기' 수 + 발전량 평균 범위(lo~hi) — 종합분석용."""
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)
    sc = _scada()
    df = sc[(sc["turbine_code"] != turbine_code) & (sc["timestamp"] >= win_start)
            & (sc["timestamp"] < win_end)]
    if df.empty:
        return {"n": 0, "lo": None, "hi": None}
    means = df.groupby("turbine_code")["power_output"].mean()
    return {"n": int(means.shape[0]), "lo": _num(means.min()), "hi": _num(means.max())}


def get_scada_series(turbine_code, start_time, event_type) -> list:
    """prolonged_stop 24h 시간별 (기대, 실측) — 라인차트용. 그 외 빈 리스트."""
    if event_type != "prolonged_stop":
        return []
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)
    sc = _scada()
    df = sc[(sc["turbine_code"] == turbine_code) & (sc["timestamp"] >= win_start)
            & (sc["timestamp"] < win_end)]
    return [{"t": r["timestamp"].strftime("%H:%M"), "expected": _num(r["expected_power_pooled"]),
             "actual": _num(r["power_output"])} for _, r in df.iterrows()]


def get_powercurve_bins(turbine_code, start_time, event_type, bin_w=2) -> list:
    """만성: 30일 풍속 구간별 (실측 평균, 기대 평균) — 파워커브용. 그 외 빈 리스트."""
    if event_type not in _CHRONIC:
        return []
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)
    sc = _scada()
    df = sc[(sc["turbine_code"] == turbine_code) & (sc["timestamp"] >= win_start)
            & (sc["timestamp"] < win_end) & (sc["wind_speed"] > 0)].copy()
    if df.empty:
        return []
    df["wbin"] = (df["wind_speed"] // bin_w * bin_w).astype(int)
    g = df.groupby("wbin").agg(actual=("power_output", "mean"), expected=("expected_power_pooled", "mean"))
    return [{"w": int(wbin), "actual": _num(r["actual"]), "expected": _num(r["expected"])} for wbin, r in g.iterrows()]


def get_farm_presence(start_time, event_type) -> list:
    """data_missing: 부재창 동안 호기별 관측 행 수(0=부재) — 막대차트/부재범위용. 그 외 빈 리스트."""
    if event_type != "data_missing":
        return []
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)
    sc = _scada()
    df = sc[(sc["timestamp"] >= win_start) & (sc["timestamp"] < win_end)]
    counts = df.groupby("turbine_code").size().to_dict()
    return [{"turbine": t, "rows": int(counts.get(t, 0))} for t in _farm_turbines()]


def get_recent_events(turbine_code, event_type, before_time, months=RECENT_HISTORY_MONTHS) -> list:
    """최근 N개월 같은 유형 발생 내역(이 이벤트 이전, 최신순). 건별 dict 목록 반환.

    [{start_time, tier, estimated_loss_kwh}, ...] — count가 아니라 '내역'을 준다.
    """
    before = pd.to_datetime(before_time)
    lo = before - pd.DateOffset(months=months)
    ev = _events()
    df = ev[(ev["turbine_code"] == turbine_code) & (ev["event_type"] == event_type)
            & (ev["start_time"] >= lo) & (ev["start_time"] < before)]
    df = df.sort_values("start_time", ascending=False)
    return [{"start_time": _num(r["start_time"]), "tier": r["tier"],
             "estimated_loss_kwh": _num(r["estimated_loss_kwh"])} for _, r in df.iterrows()]


def get_weather(turbine_code, start_time) -> dict:
    ts = pd.to_datetime(start_time).floor("h")
    w = _weather()
    row = w[w["timestamp"] == ts]
    if row.empty:
        return {"found": False}
    r = row.iloc[0]
    return {"found": True, "temperature": _num(r["temperature"]), "precipitation": _num(r["precipitation"]),
            "pressure": _num(r["pressure"]), "humidity": _num(r["humidity"]), "wind_direction": _num(r["wind_direction"])}


def fetch(event_id: int) -> dict:
    """event_id → tool_outputs (표·차트·종합분석 공용 단일 출처)."""
    event = get_anomaly_event(event_id)
    if not event.get("found"):
        return {"event": event}
    code, start, et = event["turbine_code"], event["start_time"], event["event_type"]
    recent_events = get_recent_events(code, et, start)   # 최근 6개월 건별 내역
    return {
        "event": event,
        "scada": get_scada_during_event(code, start, et),
        "others": get_other_turbines(code, start, et),
        "weather": get_weather(code, start),
        "recent_events": recent_events,
        "recent_count": {"count": len(recent_events)},   # 표용(이제 최근 6개월 창)
        "series": get_scada_series(code, start, et),
        "powercurve": get_powercurve_bins(code, start, et),
        "presence": get_farm_presence(start, et),
    }
