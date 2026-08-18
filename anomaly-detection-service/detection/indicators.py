"""계층 B 지표 — 30일 단지상대 에너지비.

energy_ratio_30d = 호기 30일 (Σ실측 / Σ기대) − 단지 중앙값

단지 중앙값을 빼는 이유: 계절·기상·단지 공통 출력제어 같은 **공통 요인을 상쇄**하기
위해서다. 흔히 "터빈별 모델의 자기은폐 방어"로 설명되지만 그건 성립하지 않는다 —
기대값에 이미 흡수된 편차는 중앙값 차감으로 되살아나지 않는다. 자기은폐는
**통합 모델 기대값(expected_power_pooled)을 쓰는 것**으로 막는다 (scoring.py 참조).

실측 근거 (장흥 전 기간, U6):
    energy_ratio_30d 평균   pooled −0.134  vs  per_unit −0.091
    선별 임계 통과 비율      pooled  67.2%  vs  per_unit  44.8%
터빈별 기대값을 쓰면 만성 열위 구간의 22%p를 놓친다.
"""
import numpy as np
import pandas as pd

TS = "일자"
UNIT = "발전호기"
PW = "계통 유효 전력(킬로와트)"

WINDOW_DAYS = 30
MIN_PERIODS = 20   # 30일 창에서 최소 이만큼은 유효해야 비율을 낸다


def usable_mask(scored: pd.DataFrame, expected_col: str) -> pd.Series:
    """집계에서 뺄 구간. 행을 삭제하는 게 아니라 합계에서 제외할 뿐이다.

    - 정지(flag_highwind_neg): 발전 안 한 시간을 저하로 세면 안 된다
    - 기대·실측 결측

    출력제어(단지 공통)는 여기서 따로 빼지 않는다 — 단지 중앙값 차감(energy_ratio_30d)에서
    판정 전 상쇄되기 때문. (과거 flag_plateau로 제외했으나 1시간 관측에선 항상 0이었다.)
    """
    return (
        (scored["flag_highwind_neg"].fillna(0) == 0)
        & scored[expected_col].notna()
        & scored[PW].notna()
    )


def daily_sums(scored: pd.DataFrame, expected_col: str):
    """(호기 × 날짜) 실측합·기대합 피벗. 계층 B의 모든 계산이 여기서 출발한다."""
    w = scored[usable_mask(scored, expected_col)].copy()
    w["date"] = w[TS].dt.floor("1D")
    daily = w.groupby([UNIT, "date"]).agg(act=(PW, "sum"), exp=(expected_col, "sum")).reset_index()
    act = daily.pivot(index="date", columns=UNIT, values="act").sort_index()
    exp = daily.pivot(index="date", columns=UNIT, values="exp").sort_index()
    return act, exp


def energy_ratio_30d(act: pd.DataFrame, exp: pd.DataFrame,
                     window: int = WINDOW_DAYS, min_periods: int = MIN_PERIODS) -> pd.DataFrame:
    """30일 누적 에너지비에서 단지 중앙값을 뺀 값. 반환: (날짜 × 호기)."""
    ratio = (act.rolling(window, min_periods=min_periods).sum()
             / exp.rolling(window, min_periods=min_periods).sum())
    return ratio.sub(ratio.median(axis=1), axis=0)


def energy_ratio_with_deficit(act: pd.DataFrame, exp: pd.DataFrame, unit, delta: float,
                              window: int = WINDOW_DAYS, min_periods: int = MIN_PERIODS) -> pd.Series:
    """호기 `unit`에 비율 결손 delta를 주입했을 때의 energy_ratio_30d.

    실측을 (1−delta)배 하면 30일 누적합도 정확히 (1−delta)배가 되므로 비율을
    다시 굴릴 필요 없이 스케일만 하면 된다 (근사가 아니라 항등).

    단지 중앙값은 **주입 후에 다시 계산한다.** 한 호기가 내려가면 중앙값도
    움직이므로, 재계산하지 않으면 결손 효과를 과대평가한다.
    """
    ratio = (act.rolling(window, min_periods=min_periods).sum()
             / exp.rolling(window, min_periods=min_periods).sum())
    injected = ratio.copy()
    injected[unit] = injected[unit] * (1.0 - delta)
    return injected[unit] - injected.median(axis=1)
