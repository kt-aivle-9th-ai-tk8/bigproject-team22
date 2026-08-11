"""데이터 조회 — LLM 절대 사용 안 함. 반환 수치는 원본 그대로(가공 금지).

소스 선택(CSV/RDS)은 app.core.datasource 가 테이블 단위로 한다 — 이 파일은 load_table()/
turbine_index() 만 부르고 어디서 읽는지는 신경 쓰지 않는다. RDS 에 anomaly_event·scada_record·
aws_record·asos_record 가 채워지면 datasource._RDS_TABLES 에 등재만 하면 이 파일 무수정으로 RDS 전환.
tool_outputs = builder(표·차트) + facts(종합분석 인용 수치) 양쪽의 단일 출처.

스키마 정합(확정 스키마 기준):
  - scada_record / anomaly_event 는 turbine_code 가 아니라 turbine_id(FK) 를 가진다.
    → turbine_index()(turbine_id→turbine_code) 를 조인해 붙인다.
  - 측정 시각 컬럼은 recorded_at.
  - 기상: aws_record(기온·습도·강수·기압·풍향) + asos_record(적설 3종) — station_id 로 조인.
"""
import pandas as pd

from app.core.config import RECENT_HISTORY_MONTHS
from app.core.datasource import load_table, query, turbine_index

WINDOW_HOURS = {"prolonged_stop": 24, "data_missing": 6, "chronic_screening": 720, "chronic_confirmed": 720}
_CHRONIC = ("chronic_screening", "chronic_confirmed")
_SNOW_MISSING = -9   # 기상청 ASOS 결측/미관측 센티넬


# ── 데이터 접근 ────────────────────────────────────────────────────────────────
# 대상 터빈·관측창으로 좁혀 조회한다(테이블 전체를 올리지 않는다).
# 여러 테이블의 시점 일관성은 service 의 snapshot() 트랜잭션이 보장한다.
def _tcode_map() -> pd.DataFrame:
    """turbine_id → turbine_code 만 뽑은 참조표(조인용)."""
    return turbine_index()[["turbine_id", "turbine_code"]]


def _with_code(df: pd.DataFrame) -> pd.DataFrame:
    """turbine_code 부여 — scada/anomaly_event 엔 code 컬럼이 없어 참조표로 조인한다."""
    return df.merge(_tcode_map(), on="turbine_id", how="left")


def _event_by_id(event_id: int) -> pd.DataFrame:
    return _with_code(query("anomaly_event", eq={"event_id": int(event_id)}))


def _events_of(turbine_id, event_type, lo, hi) -> pd.DataFrame:
    """같은 터빈·같은 유형의 과거 이벤트(기간 [lo, hi))."""
    return query("anomaly_event",
                 eq={"turbine_id": int(turbine_id), "event_type": event_type},
                 span={"start_time": (lo, hi)})


def _scada_window(turbine_ids, win_start, win_end) -> pd.DataFrame:
    """관측창 안의 scada — 터빈 1대든 단지 전체든 turbine_id 목록으로 좁힌다."""
    return _with_code(query("scada_record",
                            isin={"turbine_id": [int(t) for t in turbine_ids]},
                            span={"recorded_at": (win_start, win_end)}))


def _aws_at(station_id, ts) -> pd.DataFrame:
    return query("aws_record", eq={"aws_station_id": station_id, "recorded_at": ts})


def _asos_at(station_id, ts) -> pd.DataFrame:
    return query("asos_record", eq={"asos_station_id": station_id, "recorded_at": ts})


def _farm_turbine_ids(wind_farm_id) -> set:
    """해당 단지의 turbine_id 집합 — scada/이벤트를 '같은 단지'로 좁히는 데 쓴다.
    turbine_code(U1~U8)는 단지 간 중복이라 스코프에 못 쓴다 — 반드시 turbine_id 로 좁힌다.
    wind_farm_id 가 None(FK 깨짐)이면 빈 집합(보수적으로 '타 단지 혼입 없음')."""
    if wind_farm_id is None:
        return set()
    ti = turbine_index()
    return set(ti[ti["wind_farm_id"] == wind_farm_id]["turbine_id"].tolist())


def _farm_turbines(wind_farm_id) -> list:
    """해당 단지의 turbine_code 목록(정렬) — presence 막대차트의 호기 로스터."""
    if wind_farm_id is None:
        return []
    ti = turbine_index()
    return sorted(ti[ti["wind_farm_id"] == wind_farm_id]["turbine_code"].dropna().unique().tolist())


def _farm_row(wind_farm_id):
    """wind_farm 1행(단지명·aws/asos station id) — 없으면 None."""
    if wind_farm_id is None:
        return None
    wf = load_table("wind_farm")
    row = wf[wf["wind_farm_id"] == wind_farm_id]
    return row.iloc[0] if not row.empty else None


def _num(v):
    if pd.isna(v):
        return None
    if isinstance(v, pd.Timestamp):
        return v.isoformat(sep=" ")
    return v.item() if hasattr(v, "item") else v


def _snow(v):
    """적설 값 정규화 — 결측 센티넬(-9)은 None, 그 외는 그대로."""
    n = _num(v)
    if n is None or (isinstance(n, (int, float)) and n <= _SNOW_MISSING):
        return None
    return n


def _observation_window(start, event_type):
    """만성은 [start-30일, start], 급성은 [start, start+H]."""
    delta = pd.Timedelta(hours=WINDOW_HOURS.get(event_type, 24))
    return (start - delta, start) if event_type in _CHRONIC else (start, start + delta)


def get_anomaly_event(event_id: int) -> dict:
    row = _event_by_id(event_id)
    if row.empty:
        return {"found": False}
    r = row.iloc[0]
    tid = int(r["turbine_id"])
    ti = turbine_index()
    trow = ti[ti["turbine_id"] == tid]
    fid = None
    if not trow.empty and pd.notna(trow["wind_farm_id"].iloc[0]):
        fid = int(trow["wind_farm_id"].iloc[0])
    farm = _farm_row(fid)   # 단지명·station id (wind_farm 테이블)
    end = _num(r["end_time"])
    return {
        "found": True, "event_id": int(r["event_id"]),
        "turbine_id": tid, "turbine_code": _num(r["turbine_code"]),
        "wind_farm_id": fid,
        "wind_farm_name": (farm["wind_farm_name"] if farm is not None else None),
        "aws_station_id": (_num(farm["aws_station_id"]) if farm is not None else None),
        "asos_station_id": (_num(farm["asos_station_id"]) if farm is not None else None),
        "tier": r["tier"], "event_type": r["event_type"], "scope": _num(r["scope"]),
        "start_time": _num(r["start_time"]), "end_time": end, "ongoing": end is None,
        "expected_power": _num(r["expected_power"]), "actual_power": _num(r["actual_power"]),
        "z_score": _num(r["z_score"]), "deviation_pct": _num(r["deviation_pct"]),
        "energy_ratio_30d": _num(r["energy_ratio_30d"]), "estimated_loss_kwh": _num(r["estimated_loss_kwh"]),
    }


def get_scada_during_event(turbine_id, start_time, event_type) -> dict:
    """관측창 scada 집계(표·종합분석 공용). 평균·최대 풍속, 기대/실측 누적.

    turbine_id 로 필터한다 — turbine_code(U1~U8)는 단지 간 중복이라 멀티팜에서 타 단지가 섞인다."""
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)
    df = _scada_window([turbine_id], win_start, win_end)
    if df.empty:
        return {"found": False, "n_rows": 0}
    return {
        "found": True, "n_rows": len(df),
        "avg_wind_speed": _num(df["wind_speed"].mean()), "max_wind_speed": _num(df["wind_speed"].max()),
        "avg_power": _num(df["power_output"].mean()),
        "sum_expected_power_pooled": _num(df["expected_power_pooled"].sum()),
        "sum_actual_power": _num(df["power_output"].sum()),
    }


def get_other_turbines(turbine_id, wind_farm_id, start_time, event_type) -> dict:
    """관측창 동안 '같은 단지 내 타 호기' 수 + 발전량 평균 범위(lo~hi) — 종합분석용.

    반드시 단지로 좁힌다 — turbine_code 로 '!= 대상' 만 걸면 멀티팜에서 타 단지 호기까지 '타 호기'로 샌다."""
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)
    others = _farm_turbine_ids(wind_farm_id) - {turbine_id}
    df = _scada_window(others, win_start, win_end)
    if df.empty:
        return {"n": 0, "lo": None, "hi": None}
    means = df.groupby("turbine_id")["power_output"].mean()
    return {"n": int(means.shape[0]), "lo": _num(means.min()), "hi": _num(means.max())}


def get_scada_series(turbine_id, start_time, event_type) -> list:
    """prolonged_stop 24h 시간별 (기대, 실측) — 라인차트용. 그 외 빈 리스트."""
    if event_type != "prolonged_stop":
        return []
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)
    df = _scada_window([turbine_id], win_start, win_end)
    return [{"t": r["recorded_at"].strftime("%H:%M"), "expected": _num(r["expected_power_pooled"]),
             "actual": _num(r["power_output"])} for _, r in df.iterrows()]


def get_powercurve_bins(turbine_id, start_time, event_type, bin_w=2) -> list:
    """만성: 30일 풍속 구간별 (실측 평균, 기대 평균) — 파워커브용. 그 외 빈 리스트."""
    if event_type not in _CHRONIC:
        return []
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)
    df = _scada_window([turbine_id], win_start, win_end)
    df = df[df["wind_speed"] > 0].copy()
    if df.empty:
        return []
    df["wbin"] = (df["wind_speed"] // bin_w * bin_w).astype(int)
    g = df.groupby("wbin").agg(actual=("power_output", "mean"), expected=("expected_power_pooled", "mean"))
    return [{"w": int(wbin), "actual": _num(r["actual"]), "expected": _num(r["expected"])} for wbin, r in g.iterrows()]


def get_farm_presence(wind_farm_id, start_time, event_type) -> list:
    """data_missing: 부재창 동안 호기별 관측 행 수(0=부재) — 막대차트/부재범위용. 그 외 빈 리스트.

    같은 단지로 좁혀 집계한다 — 전 단지 scada 를 그대로 세면 멀티팜에서 타 단지 호기가 로스터에 섞인다."""
    if event_type != "data_missing":
        return []
    start = pd.to_datetime(start_time)
    win_start, win_end = _observation_window(start, event_type)
    df = _scada_window(_farm_turbine_ids(wind_farm_id), win_start, win_end)
    counts = df.groupby("turbine_code").size().to_dict()   # 단지 내에선 turbine_code 가 유일
    return [{"turbine": t, "rows": int(counts.get(t, 0))} for t in _farm_turbines(wind_farm_id)]


def get_recent_events(turbine_id, event_type, before_time, months=RECENT_HISTORY_MONTHS) -> list:
    """최근 N개월 같은 유형 발생 내역(이 이벤트 이전, 최신순). 건별 dict 목록 반환.

    [{start_time, tier, estimated_loss_kwh}, ...] — count가 아니라 '내역'을 준다.
    turbine_id 로 필터한다 — turbine_code 는 단지 간 중복이라 멀티팜에서 타 단지 이력이 섞인다.
    """
    before = pd.to_datetime(before_time)
    lo = before - pd.DateOffset(months=months)
    df = _events_of(turbine_id, event_type, lo, before)
    df = df.sort_values("start_time", ascending=False)
    return [{"start_time": _num(r["start_time"]), "tier": r["tier"],
             "estimated_loss_kwh": _num(r["estimated_loss_kwh"])} for _, r in df.iterrows()]


def get_weather(aws_station_id, asos_station_id, start_time) -> dict:
    """시작 시점 기상 — aws_record(기온·습도·강수·기압·풍향) + asos_record(적설 3종).

    두 관측망은 station_id 로 분리 조회하며, aws 가 없으면 미관측으로 처리한다.
    적설 3종(3시간 신적설·일 신적설·적설)은 asos 가 있을 때만 채운다(결측 -9 → None).
    """
    ts = pd.to_datetime(start_time).floor("h")
    row = _aws_at(aws_station_id, ts)
    if row.empty:
        return {"found": False}
    r = row.iloc[0]
    out = {"found": True, "temperature": _num(r["temperature"]), "precipitation": _num(r["precipitation"]),
           "pressure": _num(r["pressure"]), "humidity": _num(r["humidity"]),
           "wind_direction": _num(r["wind_direction"]),
           "snow_hr3": None, "snow_day": None, "snow_tot": None}
    srow = _asos_at(asos_station_id, ts)
    if not srow.empty:
        s = srow.iloc[0]
        out["snow_hr3"] = _snow(s["sd_hr3"])   # 3시간 신적설 cm
        out["snow_day"] = _snow(s["sd_day"])   # 일 신적설 cm
        out["snow_tot"] = _snow(s["sd_tot"])   # 적설 cm
    return out


def fetch(event_id: int) -> dict:
    """event_id → tool_outputs (표·차트·종합분석 공용 단일 출처)."""
    event = get_anomaly_event(event_id)
    if not event.get("found"):
        return {"event": event}
    # 멀티팜 안전: scada/이벤트 필터는 turbine_id(유일)로, 단지 스코프는 wind_farm_id 로 한다.
    # turbine_code(U1~U8)는 단지 간 중복이라 필터에 쓰면 타 단지가 섞인다.
    tid, fid = event["turbine_id"], event["wind_farm_id"]
    start, et = event["start_time"], event["event_type"]
    recent_events = get_recent_events(tid, et, start)   # 최근 6개월 건별 내역
    return {
        "event": event,
        "scada": get_scada_during_event(tid, start, et),
        "others": get_other_turbines(tid, fid, start, et),
        "weather": get_weather(event["aws_station_id"], event["asos_station_id"], start),
        "recent_events": recent_events,
        "recent_count": {"count": len(recent_events)},   # 표용(이제 최근 6개월 창)
        "series": get_scada_series(tid, start, et),
        "powercurve": get_powercurve_bins(tid, start, et),
        # presence(전 호기 관측 상태)는 scope=farm 의 '동시 부재' 증거로만 필요 —
        # scope=turbine 은 탐지기 판정을 그대로 서술하므로 farm-wide scada 를 읽지 않는다.
        "presence": get_farm_presence(fid, start, et) if event.get("scope") == "farm" else [],
    }
