"""anomaly 데이터 조회 — LLM 절대 사용 안 함.

반환 수치는 전부 원본 그대로(반올림·가공 금지). 이 값들이 critic의 검증 기준이 된다.
데이터는 모두 화순(U1~U8) · 2024~2025 범위.

배포(RDS) 전환: app.core.config.DATA_SOURCE == "rds" 이면 아래 조회 함수 구현을
SQLAlchemy 쿼리로 교체(시그니처·반환 dict는 동일 유지). fetch()·graph·agent는 불변.
"""
import os
import pandas as pd

from app.core.config import DATA_DIR

# ── CSV 로드 (모듈 로드 시 1회) ────────────────────────────────────────────
# anomaly_event / scada 는 BOM 있는 UTF-8, 날씨 merged 는 일반 UTF-8
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
_weather = pd.read_csv(
    os.path.join(DATA_DIR, "aws_record.csv"),
    parse_dates=["timestamp"],
)


def _num(v):
    """NaN/빈값 → None, 그 외 원본 파이썬 스칼라로."""
    if pd.isna(v):
        return None
    if isinstance(v, (pd.Timestamp,)):
        return v.isoformat(sep=" ")
    return v.item() if hasattr(v, "item") else v


def get_anomaly_event(event_id: int) -> dict:
    """anomaly_event 1건 → dict. 없으면 {'found': False}."""
    row = _events[_events["event_id"] == event_id]
    if row.empty:
        return {"found": False}
    r = row.iloc[0]
    end = _num(r["end_time"])
    return {
        "found": True,
        "event_id": int(r["event_id"]),
        "turbine_code": r["turbine_code"],
        "tier": r["tier"],
        "event_type": r["event_type"],
        "scope": _num(r["scope"]),
        "start_time": _num(r["start_time"]),
        "end_time": end,
        "ongoing": end is None,
        "expected_power": _num(r["expected_power"]),
        "actual_power": _num(r["actual_power"]),
        "z_score": _num(r["z_score"]),
        "deviation_pct": _num(r["deviation_pct"]),
        "energy_ratio_30d": _num(r["energy_ratio_30d"]),
        "estimated_loss_kwh": _num(r["estimated_loss_kwh"]),
    }


# event_type별 표준 관측창(시간). 데이터 컬럼이 아니라 탐지 규칙 파라미터.
#   급성(A): 발생 이후 창.  만성(B): 지표가 30일 누적이라 트리거 이전 창.
WINDOW_HOURS = {
    "prolonged_stop": 24,
    "data_missing": 6,
    "chronic_screening": 720,   # 30일
    "chronic_confirmed": 720,   # 30일
}
_CHRONIC = ("chronic_screening", "chronic_confirmed")


def _observation_window(start, event_type):
    """(win_start, win_end) 반환. 만성은 [start-30일, start], 급성은 [start, start+H]."""
    hours = WINDOW_HOURS.get(event_type, 24)
    delta = pd.Timedelta(hours=hours)
    if event_type in _CHRONIC:
        return start - delta, start
    return start, start + delta


def get_scada_during_event(turbine_code: str, start_time, event_type: str) -> dict:
    """관측창 scada 집계. 창은 event_type별 표준 관측창으로 결정(duration_hours 미사용).

    반환: n_rows, avg_wind_speed, avg_power, avg_norm_wind_speed,
          sum_expected_power_pooled, sum_actual_power
    """
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)

    df = _scada[
        (_scada["turbine_code"] == turbine_code)
        & (_scada["timestamp"] >= win_start)
        & (_scada["timestamp"] < win_end)
    ]
    n = len(df)
    if n == 0:
        return {"found": False, "n_rows": 0}

    return {
        "found": True,
        "n_rows": n,
        "avg_wind_speed": _num(df["wind_speed"].mean()),
        "avg_power": _num(df["power_output"].mean()),
        "avg_norm_wind_speed": _num(df["norm_wind_speed"].mean()),
        "sum_expected_power_pooled": _num(df["expected_power_pooled"].sum()),
        "sum_actual_power": _num(df["power_output"].sum()),
    }


def get_scada_series(turbine_code: str, start_time, event_type: str) -> list:
    """관측창의 시간별 (expected_power_pooled, power_output) 시계열 — 스파크라인용.

    prolonged_stop(24h)에만 의미가 있어 그 외 유형은 빈 리스트를 반환한다.
    반환: [{t: "HH:MM", expected: float|None, actual: float|None}, ...]
    """
    if event_type != "prolonged_stop":
        return []
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)
    df = _scada[
        (_scada["turbine_code"] == turbine_code)
        & (_scada["timestamp"] >= win_start)
        & (_scada["timestamp"] < win_end)
    ]
    return [
        {
            "t": r["timestamp"].strftime("%H:%M"),
            "expected": _num(r["expected_power_pooled"]),
            "actual": _num(r["power_output"]),
        }
        for _, r in df.iterrows()
    ]


def get_powercurve_bins(turbine_code: str, start_time, event_type: str, bin_w: int = 2) -> list:
    """만성 성능저하: 30일 관측창의 풍속 구간별 (실측 평균, 기대 평균) — 파워커브용.

    chronic_*에만 의미. 그 외 유형은 빈 리스트.
    반환: [{w: 풍속하한, actual: float, expected: float}, ...] (풍속 오름차순)
    """
    if event_type not in ("chronic_screening", "chronic_confirmed"):
        return []
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)  # [start-30d, start]
    df = _scada[
        (_scada["turbine_code"] == turbine_code)
        & (_scada["timestamp"] >= win_start)
        & (_scada["timestamp"] < win_end)
        & (_scada["wind_speed"] > 0)
    ].copy()
    if df.empty:
        return []
    df["wbin"] = (df["wind_speed"] // bin_w * bin_w).astype(int)
    g = df.groupby("wbin").agg(actual=("power_output", "mean"),
                               expected=("expected_power_pooled", "mean"))
    return [
        {"w": int(wbin), "actual": _num(r["actual"]), "expected": _num(r["expected"])}
        for wbin, r in g.iterrows()
    ]


# 단지 호기 목록 (scada에 존재하는 터빈). data_missing scope 시각화용.
_FARM_TURBINES = sorted(_scada["turbine_code"].unique().tolist())


def get_farm_presence(start_time, event_type: str) -> list:
    """data_missing: 부재 관측창(6h) 동안 호기별 관측 행 수. 0 = 부재.

    전 호기 0 → 수집·통신 장애(scope=farm), 한 호기만 0 → 설비 문제(scope=turbine).
    data_missing에만 의미. 그 외 빈 리스트.
    """
    if event_type != "data_missing":
        return []
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)
    df = _scada[(_scada["timestamp"] >= win_start) & (_scada["timestamp"] < win_end)]
    counts = df.groupby("turbine_code").size().to_dict()
    return [{"turbine": t, "rows": int(counts.get(t, 0))} for t in _FARM_TURBINES]


def get_recent_event_count(turbine_code: str, event_type: str, before_time) -> dict:
    """해당 터빈의 같은 유형 이벤트 중 before_time 이전 건수 → {'count': N}."""
    before = pd.to_datetime(before_time)
    df = _events[
        (_events["turbine_code"] == turbine_code)
        & (_events["event_type"] == event_type)
        & (_events["start_time"] < before)
    ]
    return {"count": int(len(df))}


def get_weather(turbine_code: str, start_time) -> dict:
    """이벤트 시작 시점 날씨 (화순 741, 2024~2025 · 시간별 정시).

    start_time 을 정시로 내림해 해당 timestamp 행 조회.
    풍속은 SCADA 나셀값을 쓰므로 여기서 제공하지 않는다. 범위 밖이면 {'found': False}.
    """
    ts = pd.to_datetime(start_time).floor("h")
    row = _weather[_weather["timestamp"] == ts]
    if row.empty:
        return {"found": False}
    r = row.iloc[0]
    return {
        "found": True,
        "timestamp": _num(r["timestamp"]),
        "temperature": _num(r["temperature"]),
        "precipitation": _num(r["precipitation"]),
        "pressure": _num(r["pressure"]),
        "humidity": _num(r["humidity"]),
        "wind_direction": _num(r["wind_direction"]),
    }


def fetch(event_id: int) -> dict:
    """event_id → tool_outputs. 이벤트 + scada 집계 + 날씨 + 최근 건수를 묶는다.

    (계약) 반환 dict가 그대로 state['tool_outputs']가 되고 critic 검증 기준이 된다.
    """
    event = get_anomaly_event(event_id)
    if not event.get("found"):
        return {"event": event}

    code = event["turbine_code"]
    return {
        "event": event,
        "scada": get_scada_during_event(code, event["start_time"], event["event_type"]),
        "weather": get_weather(code, event["start_time"]),
        "recent_count": get_recent_event_count(
            code, event["event_type"], event["start_time"]
        ),
        "series": get_scada_series(code, event["start_time"], event["event_type"]),
        "powercurve": get_powercurve_bins(code, event["start_time"], event["event_type"]),
        "presence": get_farm_presence(event["start_time"], event["event_type"]),
    }
