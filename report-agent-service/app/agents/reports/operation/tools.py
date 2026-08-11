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

from app.core.datasource import load_table, query, table_available


# ── 데이터 접근 ────────────────────────────────────────────────────────────────
# 큰 테이블(scada_record·anomaly_event)은 통째로 읽지 않고 대상 터빈·기간으로 좁혀 조회한다.
# 보고서 1건이 읽는 여러 테이블의 시점 일관성은 service 의 snapshot() 트랜잭션이 보장한다.
def _scada_of(turbine_id, start=None, end_excl=None):
    """터빈 1대의 scada — 기간이 주어지면 그 범위만."""
    return query("scada_record",
                 eq={"turbine_id": int(turbine_id)},
                 span={"recorded_at": (start, end_excl)} if (start or end_excl) else None)


def _events_of(turbine_id, start=None, end_excl=None):
    """터빈 1대의 이상 이벤트 — start_time 기준 기간 필터."""
    return query("anomaly_event",
                 eq={"turbine_id": int(turbine_id)},
                 span={"start_time": (start, end_excl)} if (start or end_excl) else None)


def _turbine():
    return load_table("turbine")


def _farm():
    return load_table("wind_farm")


def _inspection_of(turbine_id, start, end_excl):
    """기간 내 그 터빈의 점검 — 소스가 없으면 None(미가용)."""
    if not table_available("inspection"):
        return None
    return query("inspection",
                 eq={"turbine_id": int(turbine_id)},
                 span={"inspection_start": (start, end_excl)})


def _defect_of(inspection_ids):
    """해당 점검들의 결함 — 소스가 없으면 None(미가용)."""
    if not table_available("defect"):
        return None
    return query("defect", isin={"inspection_id": list(inspection_ids)})


def known_turbine_ids() -> list:
    """scada 에 실적이 있는 turbine_id 목록.

    존재 확인용이라 turbine 참조표를 쓴다 — scada 를 통째로 읽어 unique() 하면
    보고서 1건에 14만 행을 읽게 된다(그 터빈에 실적이 있는지는 _scada_of 가 비었는지로 판정).
    """
    return sorted(_turbine()["turbine_id"].dropna().unique().tolist())


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
    df = _events_of(turbine_id, start, end_excl)
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


def _period_args(period_start=None, period_end=None):
    """요청 기간 → (SQL 하한, SQL 상한). 미지정이면 그 방향 무제한(None).

    period_end는 그 날짜 '끝까지' 포함(해당 일 23:59:59.999…)하도록 다음 날 00:00을 exclusive 상한으로 쓴다.
    예전에는 df 에서 min/max 를 구했는데, 그러려면 테이블을 먼저 통째로 읽어야 했다.
    지금은 명시된 값만 SQL 로 내리고, 실제 관측 구간은 조회 결과에서 읽는다.
    """
    start = pd.to_datetime(period_start).normalize() if period_start else None
    end_excl = (pd.to_datetime(period_end).normalize() + pd.Timedelta(days=1)
                if period_end else None)
    return start, end_excl


def get_scada_summary(turbine_id, period_start=None, period_end=None) -> dict:
    """터빈 scada 집계 + 운영 진단 지표 + 월별 시계열. 기간 미지정 시 관측 전 구간."""
    start, end_excl = _period_args(period_start, period_end)
    df = _scada_of(turbine_id, start, end_excl)
    if df.empty:
        reason = "해당 기간 관측 데이터 없음" if (start or end_excl) else "관측 데이터 없음"
        return {"found": False, "n_rows": 0, "reason": reason}

    # 집계 경계로 쓸 값 — 기간을 안 줬으면 실제 관측 구간이 곧 요청 구간이다.
    if start is None:
        start = df["recorded_at"].min()
    if end_excl is None:
        end_excl = df["recorded_at"].max() + pd.Timedelta(seconds=1)

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
    insp = _inspection_of(turbine_id, start, end_excl)
    if insp is None:
        return {"available": False, "n_inspections": 0, "count": 0, "high_severity": 0}

    d = _defect_of(insp["inspection_id"])
    if d is None:
        return {"available": False, "n_inspections": 0, "count": 0, "high_severity": 0}
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
