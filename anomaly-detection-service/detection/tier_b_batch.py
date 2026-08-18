"""계층 B — 만성 저하 (30일 단지상대 에너지비, 일 단위 배치).

기대값은 **단지 통합 모델(expected_power_pooled) 고정**이다. 터빈별 기대값을 쓰면
저성과 호기의 낮은 커브를 정상으로 학습해 잔차가 0에 수렴하고(자기은폐), 단지
중앙값을 빼도 되살아나지 않는다. 실측: U6 선별 통과율 pooled 67.2% vs per_unit 44.8%.

임계를 분위수로 잡지 않는 이유:
  널(정상 표본)에 실제 열위 호기가 섞여 있어 FPR이라는 말이 성립하지 않고,
  30일 rolling이라 인접 날짜가 거의 같은 값이라 표본 독립성도 깨진다.
  무엇보다 "왜 −11%인가"에 답할 근거가 없다.

대신 **노이즈 척도를 추정해 유의성으로 판정한다**:
    SE_noise(farm) = median over units of ( std of rel_u over 비중첩 30일 창 )

- 비중첩 창이라 자기상관이 제거된다 (장흥 호기당 49개 창)
- std는 각 호기의 **평균을 제거**하므로 level 차이가 SE를 오염시키지 않는다.
  실측 검증: 전 호기 0.0563 vs U6 제외 0.0562 — U6의 상시 −13%가 SE에 영향 없음
- 호기 간 중앙값을 취해 한 호기의 큰 변동성이 전체를 흔들지 않게 한다

SE_noise는 단일값이다. 모든 SCADA가 1시간 단일 관측이라 수집 주기 구분이 없다.
교차 검증: 학습기간(≤2023) 기준으로 장흥 0.049 / 화순 0.048 — 서로 다른 제조사·정격의
두 단지가 독립적으로 ~4.8%p에 수렴한다.
"""
import sys

import numpy as np
import pandas as pd

from .events import TS, UNIT, assign_priority, finalize, find_runs, turbine_code
from .indicators import WINDOW_DAYS, daily_sums, energy_ratio_30d

# 유의수준 → z 임계. 정규분포 가정은 **rel(30일 집계)** 에만 적용한다.
# 시간 단위 잔차는 좌비대칭·두꺼운 꼬리라 정규 가정이 성립하지 않지만,
# 720시간을 누적한 rel은 중심극한정리로 정규에 가까워진다.
Z_SCREENING = -1.645   # p < 0.05
Z_CONFIRMED = -2.326   # p < 0.01

MIN_WINDOWS = 20       # SE 추정에 필요한 최소 비중첩 창 수 (미만이면 경고)

# SE_noise 기준 기간의 끝. 모델 학습 구간(train ≤2023)과 일치시킨다.
# 판정 대상 기간까지 넣어 SE를 재면 **이상이 자기 척도를 부풀려 자기를 숨기는
# 순환**이 생긴다 — 화순 실증: U2·U5가 2024~25에 붕괴하자 전기간 SE가
# 0.14로 부풀어 확정 임계가 −33%까지 밀렸다. 학습구간으로 제한하면 0.048.
# 교차 검증: 같은 기준으로 장흥 0.049 — 두 단지가 독립적으로 ~0.048에 수렴한다.
# (std는 level 오염에는 면역이지만, 기간 내 '추세'는 변동으로 잡히기 때문)
SE_REFERENCE_END_YEAR = 2023

SEVERITY = {"chronic_confirmed": 0, "chronic_screening": 1}


def estimate_se_noise(rel: pd.DataFrame,
                      reference_end_year: int | None = SE_REFERENCE_END_YEAR) -> float:
    """정상 변동폭 SE_noise (단일값). 호기별 비중첩 30일 창 std의 중앙값.

    비중첩 창만 사용한다. rolling 값을 그대로 쓰면 인접 날짜가 거의 같은 값이라
    표본 수가 부풀려지고 SE가 과소추정된다.

    reference_end_year 이후의 창은 SE 추정에서 제외한다(판정에는 전 기간 사용).
    기대값 모델이 학습한 것과 같은 '깨끗한 기준 기간'에서 정상 변동폭을 재고,
    그 자로 학습 이후 구간을 판정하는 구조다.

    모든 SCADA가 1시간 단일 관측으로 통일돼 수집 주기 구분이 없다(단일 SE).
    """
    ref = rel
    if reference_end_year is not None:
        ref = rel[pd.to_datetime(rel.index).year <= reference_end_year]
    r = ref.loc[ref.index[::WINDOW_DAYS]]      # 비중첩 창
    stds = [r[u].dropna().std() for u in r.columns]
    stds = [x for x in stds if np.isfinite(x)]
    return float(np.median(stds)) if stds else np.nan


def detect(scored: pd.DataFrame, farm_code: str) -> pd.DataFrame:
    """계층 B 판정. 입력은 scoring.score() 출력."""
    act, exp = daily_sums(scored, "expected_power_pooled")
    rel = energy_ratio_30d(act, exp)

    se = estimate_se_noise(rel)

    events: list[dict] = []
    for unit in rel.columns:
        series = rel[unit]
        z = series / se

        # 선별 구간으로 병합한 뒤, 그 안에서 확정에 도달했는지로 등급을 정한다.
        # 두 마스크로 각각 병합하면 같은 사건이 두 이벤트로 쪼개진다.
        screening = z < Z_SCREENING
        last = len(rel) - 1
        for a, b in find_runs(screening):
            seg_z = z.iloc[a:b + 1]
            seg_rel = series.iloc[a:b + 1]
            dates = rel.index[a:b + 1]
            confirmed = bool((seg_z < Z_CONFIRMED).any())

            seg_act = act[unit].reindex(dates)
            seg_exp = exp[unit].reindex(dates)
            loss = float((seg_exp - seg_act).sum())

            events.append({
                "farm_code": farm_code,
                "turbine_code": turbine_code(unit),
                "tier": "B",
                "event_type": "chronic_confirmed" if confirmed else "chronic_screening",
                "start_time": dates[0],
                "end_time": pd.NaT if b == last else dates[-1],
                "duration_hours": int(len(dates) * 24),
                "z_score": float(seg_z.min()),
                "expected_power": float(seg_exp.mean() / 24) if seg_exp.notna().any() else np.nan,
                "actual_power": float(seg_act.mean() / 24) if seg_act.notna().any() else np.nan,
                "deviation_pct": float(seg_rel.min() * 100),
                "energy_ratio_30d": float(seg_rel.min()),
                "estimated_loss_kwh": loss,
                "scope": "turbine",
            })

    out = assign_priority(finalize(events), SEVERITY)
    out.attrs["se_noise"] = se
    return out


if __name__ == "__main__":
    from pathlib import Path

    farm = sys.argv[1] if len(sys.argv) > 1 else "jangheung"
    if len(sys.argv) < 3:
        raise SystemExit("usage: python -m src.detect.tier_b_batch <farm> <scored.pkl>")

    ev = detect(pd.read_pickle(Path(sys.argv[2])), farm)
    se = ev.attrs["se_noise"]
    print(f"[{farm}] SE_noise = {se:.4f}"
          f"  → 도출 임계  선별 {Z_SCREENING * se * 100:.1f}%"
          f"  확정 {Z_CONFIRMED * se * 100:.1f}%")

    print(f"\n계층 B 이벤트 {len(ev)}건")
    print(ev.groupby(["turbine_code", "event_type"]).size().to_string())
    cols = ["priority_rank", "turbine_code", "event_type", "start_time", "end_time",
            "z_score", "energy_ratio_30d", "estimated_loss_kwh"]
    print("\n우선순위 상위 10건:")
    print(ev.head(10)[cols].to_string(index=False))
