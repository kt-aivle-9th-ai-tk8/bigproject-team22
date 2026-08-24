"""정지여부(is_stopped) · 학습유효여부(train_mask) 계산 — 예측 모듈(step 2) 추가분.

풍속·출력만으로 계산되며, AWS 기상·밀도·기대발전량과 무관하다.
예측 모듈(serving/ml)이 기대발전량을 채울 때 이 플래그도 같이 계산해 scada_record에 쓴다.

- is_stopped  = flag_highwind_neg (바람 충분한데 음출력 = 정지 후보). 계층 A(정지)가 씀.
- train_mask  = 정상거동 여부. **학습 전용** — 서빙 탐지엔 안 쓰인다(재학습 대비 저장/생략 선택).

주의 — undercurve(→train_mask): 풍속대별 중앙값이 필요해 **입력 데이터 범위에 의존**한다.
서빙 윈도우에서 계산하면 그 창 기준이 되므로, 학습과 정확히 맞추려면 신경 쓸 것.
is_stopped는 이 이슈 없음(단순 비교). train_mask가 부담이면 서빙에선 생략 가능.
"""
import pandas as pd

# scada_record 원본 컬럼명 (프로젝트 표준)
WS = "나셀 풍속(초당 미터)"
PW = "계통 유효 전력(킬로와트)"
UNIT = "발전호기"

UNDERCURVE_RATIO = 0.5      # 구간 중앙값 대비 이 비율 미만이면 undercurve
UNDERCURVE_MIN_MED = 0.05   # 중앙값이 정격의 5% 이상인 구간에서만 판정

# 단지별 정격(kW)·컷인(m/s). undercurve·정지 기준이 정격에 의존하므로 단지별로 넘긴다.
FARM_SPEC = {
    "jangheung": {"rated_kw": 3000.0, "cut_in_ms": 3.0},
    "hwasun":    {"rated_kw": 2000.0, "cut_in_ms": 3.0},
}


def add_flags(df: pd.DataFrame, rated_kw: float, cut_in_ms: float) -> pd.DataFrame:
    """플래그 4종 + train_mask 칼럼 추가. 행 삭제 없음.

    반환 컬럼 중 서빙에 저장할 것:
      - flag_highwind_neg → scada_record.is_stopped (0/1)
      - train_mask        → scada_record.train_mask (0/1, 학습 전용)
    입력은 (발전호기, 시각) 정렬 상태를 가정한다.
    """
    out = df.copy()
    ws, pw = out[WS], out[PW]

    # 명확 규칙
    out["flag_below_cutin"] = ws < cut_in_ms
    out["flag_lowwind_neg"] = (pw < 0) & (ws < cut_in_ms)
    out["flag_highwind_neg"] = (pw < 0) & (ws >= cut_in_ms)   # = is_stopped

    # 애매 규칙: undercurve (터빈별 0.5 m/s 구간 중앙값 대비 심한 저출력)
    bin_ws = (ws * 2).round() / 2
    bin_med = out.groupby([UNIT, bin_ws])[PW].transform("median")
    out["flag_undercurve"] = (
        (pw >= 0)
        & (ws >= cut_in_ms)
        & (bin_med >= UNDERCURVE_MIN_MED * rated_kw)
        & (pw < UNDERCURVE_RATIO * bin_med)
    )

    # train 전용 마스크
    out["train_mask"] = ~(
        out["flag_below_cutin"]
        | out["flag_highwind_neg"]
        | out["flag_undercurve"]
    )
    return out


def is_stopped(wind_speed, power_output, cut_in_ms: float) -> int:
    """단건용 정지여부 (0/1). 바람 충분(≥컷인)한데 출력 음수면 1."""
    if wind_speed is None or power_output is None:
        return 0
    return int(power_output < 0 and wind_speed >= cut_in_ms)
