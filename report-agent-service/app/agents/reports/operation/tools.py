"""operation(운영) 데이터 조회 — LLM 절대 사용 안 함.

터빈별 운영 보고서는 '터빈 + 기간' 단위지만, 공유 계약은 fetch(event_id: int) 하나다.
그래서 event_id를 '터빈 번호'로 해석한다: event_id=2 → 터빈 U2, 기간=해당 터빈 scada 전 구간.
(입력 일반화(터빈+기간 params)는 shared State 변경이라 팀 합의 후 별도 진행.)

집계(요구사항 + 고도화):
  1) anomaly_event : 기간 내 유지중(end_time NULL)/종료 건수 + 유형별(정지/데이터부재/저하)
  2) scada_record  : power_output/expected_power_unit 평균·총량(MWh),
                     가동률(is_stopped)·손실 분해(정지/성능저하)·평균 풍속·월별 시계열
  (결함(defect)은 터빈별 운영 보고서에서 다루지 않기로 했다 — 단지 단위는 farm_operation 참고.)

반환 dict가 그대로 state['tool_outputs'] = critic 검증 기준. 원본 수치 그대로(가공 최소).
배포(RDS): 소스 선택은 app.core.datasource 가 테이블 단위로 한다. anomaly_event·scada_record 는
아직 RDS 에 (충분한 컬럼이) 없어 CSV 로 오지만, 등재되면 이 파일은 그대로 두고 전환된다.
"""
import pandas as pd

from app.core.datasource import load_table

DEFAULT_FARM = "화순풍력발전소"   # 데이터는 화순 U1~U8 (ERD WindFarm.wind_farm_name 대응)


# ── 데이터 접근 ────────────────────────────────────────────────────────────
# 모듈 import 시점이 아니라 호출 시점에 읽는다(datasource 가 1회 캐시). registry 가 4개 tools 를
# 전부 import 하므로, 전역 로드로 두면 anomaly 보고서 1건에도 운영용 테이블까지 다 읽게 된다.
def _events():
    return load_table("anomaly_event")


def _scada():
    return load_table("scada_record")


def known_turbines() -> list:
    """scada 에 실적이 있는 터빈 코드 목록."""
    return sorted(_scada()["turbine_code"].unique().tolist())

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
    ev = _events()
    df = ev[
        (ev["turbine_code"] == turbine_code)
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


def get_scada_summary(turbine_code) -> dict:
    """터빈 전 구간 scada 집계 + 운영 진단 지표 + 월별 시계열."""
    sc = _scada()
    df = sc[sc["turbine_code"] == turbine_code].copy()
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
    dfm["month"] = dfm["timestamp"].dt.strftime("%Y-%m")
    grp = dfm.groupby("month").agg(expected=("expected_power_unit", "mean"),
                                   actual=("power_output", "mean"))
    monthly = [{"month": m, "expected": _num(r["expected"]), "actual": _num(r["actual"])}
               for m, r in grp.iterrows()]

    return {
        "found": True,
        "n_rows": int(len(dfv)),
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


def fetch(event_id: int) -> dict:
    """event_id(=터빈 번호) → tool_outputs. 이상 이벤트·발전 실적 집계.

    'event' 키는 공유 service가 존재 여부(found)를 읽는 계약이라 함께 노출한다.
    """
    code = turbine_code_for(event_id)
    if code not in known_turbines():
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
    }
