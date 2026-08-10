"""operation(운영) 데이터 조회 — LLM 절대 사용 안 함.

실제 ERD 스키마 기준(turbine_id FK). 공유 계약은 fetch(event_id, params) 이므로
event_id를 'turbine_id'로 해석한다: event_id=2 → turbine_id 2 (turbine.turbine_code로 표기).
기간은 params(선택), 미지정 시 해당 터빈 scada 전 구간.

집계:
  1) anomaly_event : 기간 내 유지중(end_time NULL)/종료 건수 + 유형별(정지/데이터부재/저하)
  2) scada_record  : power_output/expected_power_unit 평균·총량(MWh),
                     가동률(is_stopped)·손실 분해(정지/성능저하)·평균 풍속·월별 시계열
  3) inspection/defect : 기간 내 드론 점검 횟수, 신규 결함 수, 고위험 결함 수
                         (ERD Defect→Inspection→Turbine 조인)

반환 dict가 그대로 state['tool_outputs'] = critic 검증 기준. 원본 수치 그대로(가공 최소).
소스 선택(CSV/RDS)은 app.core.datasource 가 테이블 단위로 한다 — 이 파일은 load_table()만
부르고 어디서 읽는지는 신경 쓰지 않는다.
"""
import pandas as pd

from app.core.datasource import load_table, table_available


# ── 데이터 접근 (호출 시점 로드; datasource 가 CSV/RDS 분기 + 캐시) ──────────────
# 모듈 로드 시점이 아니라 호출 시점에 읽는다. registry 가 4개 tools 를 전부 import 하므로
# 전역 로드로 두면 anomaly 보고서 1건에도 운영용 테이블까지 다 읽고, RDS 에선 그 스냅샷이
# 프로세스 내내 고정돼 새 데이터가 영원히 안 보인다. 함수로 두면 datasource 의 TTL 로 갱신된다.
def _events():
    return load_table("anomaly_event")


def _scada():
    return load_table("scada_record")


def _turbine():
    return load_table("turbine")


def _farm():
    return load_table("wind_farm")


def _inspection():
    """점검 테이블 — 소스가 없으면 None(미가용)."""
    return load_table("inspection") if table_available("inspection") else None


def _defect():
    """결함 테이블 — 소스가 없으면 None(미가용)."""
    return load_table("defect") if table_available("defect") else None


def known_turbine_ids() -> list:
    """scada 에 실적이 있는 turbine_id 목록."""
    return sorted(_scada()["turbine_id"].dropna().unique().tolist())


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
    tb = _turbine()
    row = tb[tb["turbine_id"] == int(turbine_id)]
    if row.empty:
        return {"found": False}
    r = row.iloc[0]
    farm_name = None
    wf = _farm()
    fr = wf[wf["wind_farm_id"] == r["wind_farm_id"]]
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
    ev = _events()
    df = ev[
        (ev["turbine_id"] == int(turbine_id))
        & (ev["start_time"] >= start)
        & (ev["start_time"] < end_excl)
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


def _period_bounds(df, period_start=None, period_end=None):
    """(start, end_exclusive) 반환. 미지정이면 df의 관측 전 구간.

    period_end는 그 날짜 '끝까지' 포함(해당 일 23:59:59.999…)하도록 다음 날 00:00을 exclusive 상한으로 쓴다.
    """
    start = (pd.to_datetime(period_start).normalize()
             if period_start else df["recorded_at"].min())
    end_excl = (pd.to_datetime(period_end).normalize() + pd.Timedelta(days=1)
                if period_end else df["recorded_at"].max() + pd.Timedelta(seconds=1))
    return start, end_excl


def get_scada_summary(turbine_id, period_start=None, period_end=None) -> dict:
    """터빈 scada 집계 + 운영 진단 지표 + 월별 시계열. 기간 미지정 시 관측 전 구간."""
    sc = _scada()
    df = sc[sc["turbine_id"] == int(turbine_id)].copy()
    if df.empty:
        return {"found": False, "n_rows": 0}

    start, end_excl = _period_bounds(df, period_start, period_end)
    df = df[(df["recorded_at"] >= start) & (df["recorded_at"] < end_excl)]
    if df.empty:
        return {"found": False, "n_rows": 0, "reason": "해당 기간 관측 데이터 없음"}

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
        # 요청 기간 경계(_period_bounds 결과). anomaly·defect 가 '관측 데이터 범위'가 아니라
        # '요청 기간'으로 집계하도록 fetch 로 넘긴다 — 기간 앞뒤에 scada 가 없어도 그 구간 이벤트를 포함.
        "query_start": _num(start),
        "query_end_excl": _num(end_excl),
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
    insp_df = _inspection()
    defect_df = _defect()
    if insp_df is None or defect_df is None:
        return {"available": False, "n_inspections": 0, "count": 0, "high_severity": 0}

    insp = insp_df[
        (insp_df["turbine_id"] == int(turbine_id))
        & (insp_df["inspection_start"] >= start)
        & (insp_df["inspection_start"] < end_excl)
    ]
    d = defect_df[defect_df["inspection_id"].isin(insp["inspection_id"])]
    high = int((d["severity"] >= HIGH_SEVERITY).sum()) if "severity" in d.columns else 0
    types = d["defect_type"].value_counts().to_dict() if "defect_type" in d.columns else {}
    return {
        "available": True,
        "n_inspections": int(len(insp)),      # 드론 점검 횟수
        "count": int(len(d)),                  # 신규 발견 결함 수
        "high_severity": high,                 # 고위험 결함 수(severity >= 3)
        "by_type": {str(k): int(v) for k, v in list(types.items())[:5]},
    }


def fetch(event_id: int, params: dict = None) -> dict:
    """event_id(=turbine_id, 전역 유일) + params(기간) → tool_outputs.

    params: {"period_start": "YYYY-MM-DD", "period_end": "YYYY-MM-DD"} (선택).
      미지정 시 해당 터빈의 관측 전 구간. period_end는 그 날짜 끝까지 포함.
    turbine_id는 전역 유일값이라 단지 구분 없이 정확히 식별된다(turbine_code는 단지 내에서만 유일).

    'event' 키는 공유 service가 존재 여부(found)를 읽는 계약이라 함께 노출한다.
    """
    p = params or {}
    info = get_turbine_info(event_id)
    if not info.get("found") or int(event_id) not in known_turbine_ids():
        return {"event": {"found": False}, "turbine": {"found": False, "turbine_id": event_id}}

    scada = get_scada_summary(event_id, p.get("period_start"), p.get("period_end"))
    if not scada.get("found"):
        return {"event": {"found": False},
                "turbine": {"found": False, "turbine_id": event_id,
                            "reason": scada.get("reason", "관측 데이터 없음")}}

    # 표시 기간 = 실제 관측 데이터 범위(scada 표·차트가 덮는 구간).
    disp_start = pd.to_datetime(scada["period_start"])
    disp_end = pd.to_datetime(scada["period_end"])
    # 집계 경계 = 요청 기간(관측 데이터가 없는 구간의 이벤트/결함도 포함). 기간 미지정 시 데이터 범위와 동일.
    q_start = pd.to_datetime(scada["query_start"])
    q_end_excl = pd.to_datetime(scada["query_end_excl"])

    return {
        "event": {"found": True},   # service의 generic found-체크용
        "turbine": {
            "found": True,
            "turbine_id": info["turbine_id"],
            "turbine_code": info["turbine_code"],
            "farm_name": info["farm_name"],
            "period_start": disp_start.strftime("%Y-%m-%d"),
            "period_end": disp_end.strftime("%Y-%m-%d"),
        },
        "anomaly": get_anomaly_summary(event_id, q_start, q_end_excl),
        "scada": scada,
        "defect": get_defect_summary(event_id, q_start, q_end_excl),
    }
