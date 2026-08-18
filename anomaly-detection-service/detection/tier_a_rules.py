"""계층 A — 급성 이상 (정지 패턴, 매시간 배치).

주 엔진은 **잔차가 아니라 정지 패턴**이다 (architecture.md). 풍력 고장은 점진적
성능 저하보다 급성 정지로 발현되기 때문이다. 잔차 z는 참고용으로만 기록한다.

지속시간 기준이라 계층 B와 달리 σ 정규화가 필요 없다 — 잔차를 판정에 쓰지 않기
때문이다. 모든 SCADA가 1시간 단위라 두 단지에 동일하게 적용된다.

두 이벤트:
- prolonged_stop : 컷인 이상인데 음출력인 상태(flag_highwind_neg) 지속
- data_missing   : 원본 데이터 자체가 안 들어온 구간

두 문턱 (정지):
- **1h 탐지** — 정지가 1시간이라도 잡히면 이벤트 생성 → 알림·로그(운영자 즉시 가시성).
  24h를 기다리면 운영자가 이미 아는 정지를 하루 늦게 띄워 시스템 신뢰를 깬다.
- **24h 보고서 게이트** — 이벤트가 24h 이상 지속되면 report-agent(LangGraph)가 보고서를
  자동 생성한다. 비싼 산출물은 진짜 장기 정지에만. 이 게이트는 우리 코드가 아니라
  report-agent가 `event.duration_hours >= 24`로 판단한다. 우리는 이벤트를 1h부터 만들고
  duration_hours를 정확히 실어주면 된다.
- data_missing은 1h 부재가 대개 통신 순간 끊김(노이즈)이라 6h 유지(문턱 낮추지 않음).

**정지 = 고장 단정 금지.** 점검·전력망 지시·저풍속 대기 모두 가능하다. 이 모듈은
"이 구간이 이러이러했다"는 사실만 만들고, 판단은 사람이 한다(HITL).

data_missing의 scope 구분이 중요하다:
- scope='farm'    같은 시각 전 호기가 함께 부재 → SCADA 수집·통신 장애
- scope='turbine' 그 호기만 부재 → 해당 설비 문제
대응이 완전히 다르므로 하나로 뭉치면 안 된다.
실측: U5의 1,191h(49.6일) 부재는 타 호기 존재율 0.924로 turbine scope,
      2023-08-26 47h 부재는 타 호기 0.000으로 farm scope.

정지(prolonged_stop)는 scope/context를 나누지 않는다. 과거 동시 정지를 "계통 지시
정황"으로 분류했으나, 출력제어를 별도로 다루지 않기로 하면서 그 분류의 목적이
사라졌다(정지의 98%가 개별이기도 하다). 정지는 정지로만 기록하고, 원인 판단은 사람이 한다.

손실은 Σ(기대 − 실측)만 쓴다. 나셀 풍속 외삽 비중(extrapolated_share)·잔차 z_score는
제거했다 — scada_record에 없는 참고값이라, 탐지가 **저장된 값만으로** 돌게 단순화했다
(계층 A z_score는 NULL, 계층 B는 자체 계산으로 유지).
"""
import sys

import numpy as np
import pandas as pd

from .events import TS, UNIT, assign_priority, finalize, find_gaps, find_time_runs, turbine_code

# 판정 파라미터
STOP_RATE_MIN = 0.5      # 그 시간이 정지 상태. 1시간 단일 관측이라 값이 0/1 → 사실상 ==1
STOP_MIN_HOURS = 1       # 1h부터 탐지·알림·로그. 24h+는 report-agent가 보고서 자동생성(별도 게이트)
REPORT_MIN_HOURS = 24    # 참고용 상수 — 보고서 자동생성 문턱. 실제 트리거는 report-agent 몫
ABSENT_MIN_HOURS = 6     # 데이터 부재 최소 지속 (1h는 통신 순간 끊김 노이즈라 유지)
FARM_SCOPE_PRESENCE = 0.1  # 구간 중 타 호기 평균 존재율이 이 미만이면 단지 전체 장애로 본다

SEVERITY = {"prolonged_stop": 0, "data_missing": 1}

PW = "계통 유효 전력(킬로와트)"


def _loss(seg: pd.DataFrame) -> float:
    """구간 손실(kWh) = Σ(기대 − 실측). 기대 결측이면 NaN."""
    exp, act = seg["expected_power_unit"], seg[PW]
    if not exp.notna().any():
        return np.nan
    return float((exp - act).sum())


def _stop_event(g: pd.DataFrame, a: int, b: int, last: int,
                farm_code: str, unit) -> dict:
    """정지 run(행 a..b) → 이벤트. run이 마지막 관측까지면 진행 중(end_time=NaT)."""
    seg = g.iloc[a:b + 1]
    exp, act = seg["expected_power_unit"], seg[PW]
    return {
        "farm_code": farm_code,
        "turbine_code": turbine_code(unit),
        "tier": "A",
        "event_type": "prolonged_stop",
        "start_time": seg[TS].iloc[0],
        "end_time": pd.NaT if b == last else seg[TS].iloc[-1],
        "duration_hours": b - a + 1,
        "expected_power": float(exp.mean()) if exp.notna().any() else np.nan,
        "actual_power": float(act.mean()) if act.notna().any() else np.nan,
        "estimated_loss_kwh": _loss(seg),
        "scope": "turbine",
    }


def _missing_event(farm_code: str, unit, start_t, end_t, hours: int, scope: str) -> dict:
    """데이터 부재 갭 → 이벤트. 관측이 없어 기대·실측·손실은 산출 불가(NaN)."""
    return {
        "farm_code": farm_code,
        "turbine_code": turbine_code(unit),
        "tier": "A",
        "event_type": "data_missing",
        "start_time": start_t,
        "end_time": end_t,
        "duration_hours": hours,
        "scope": scope,
    }


def _gap_scope(start_t, end_t, hours: int, unit, unit_times: dict, n_units: int) -> str:
    """갭 구간에 **타 호기 관측이 얼마나 있었나**로 scope 판정.

    타 호기도 대부분 없으면 farm(수집·통신 장애), 그 호기만 없으면 turbine(설비).
    존재율 컬럼 없이 각 호기의 시각 배열에서 [start,end] 범위 개수로 센다.
    """
    n_others = n_units - 1
    if n_others <= 0 or hours <= 0:
        return "turbine"
    s = np.datetime64(pd.Timestamp(start_t))
    e = np.datetime64(pd.Timestamp(end_t))
    present = 0
    for u, times in unit_times.items():
        if u == unit:
            continue
        lo = np.searchsorted(times, s, side="left")
        hi = np.searchsorted(times, e, side="right")
        present += int(hi - lo)
    frac = present / (n_others * hours)
    return "farm" if frac < FARM_SCOPE_PRESENCE else "turbine"


def detect(scored: pd.DataFrame, farm_code: str) -> pd.DataFrame:
    """계층 A 판정. 입력은 scoring.score() 출력(1시간 단위, 빈 시간은 행 없음)."""
    s = scored.sort_values([UNIT, TS]).reset_index(drop=True)
    # 호기별 정렬된 시각 배열 (부재 scope의 타 호기 존재 판정용)
    unit_times = {u: np.sort(pd.to_datetime(g[TS].values))
                  for u, g in s.groupby(UNIT)}
    n_units = len(unit_times)

    events: list[dict] = []
    for unit, g in s.groupby(UNIT):
        g = g.reset_index(drop=True)
        last = len(g) - 1

        # 정지: 정지 상태가 시간 연속으로 STOP_MIN_HOURS 이상 (데이터 갭에서 끊김)
        stop = g["flag_highwind_neg"].fillna(False) >= STOP_RATE_MIN
        for a, b in find_time_runs(g[TS], stop):
            if (b - a + 1) >= STOP_MIN_HOURS:
                events.append(_stop_event(g, a, b, last, farm_code, unit))

        # 부재: 관측 시각 갭이 ABSENT_MIN_HOURS 이상
        for start_t, end_t, hours in find_gaps(g[TS], ABSENT_MIN_HOURS):
            scope = _gap_scope(start_t, end_t, hours, unit, unit_times, n_units)
            events.append(_missing_event(farm_code, unit, start_t, end_t, hours, scope))

    return assign_priority(finalize(events), SEVERITY)


if __name__ == "__main__":
    from pathlib import Path
    from .scoring import FarmModels, score

    farm = sys.argv[1] if len(sys.argv) > 1 else "jangheung"
    cache = Path(sys.argv[2]) if len(sys.argv) > 2 else None
    if cache is None:
        raise SystemExit("usage: python -m src.detect.tier_a_rules <farm> <scored.pkl>")

    ev = detect(pd.read_pickle(cache), farm)
    print(f"[{farm}] 계층 A 이벤트 {len(ev)}건")
    print(ev.groupby(["event_type", "scope"]).size().to_string())
    print("\n최장 10건:")
    cols = ["turbine_code", "event_type", "scope", "duration_hours", "estimated_loss_kwh"]
    print(ev.nlargest(10, "duration_hours")[cols].to_string(index=False))
