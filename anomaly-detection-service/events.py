"""이벤트 병합·상태 관리 — 계층 A/B 공용.

이게 없으면 판정이 참인 **매 시간·매일마다 anomaly_event가 하나씩 쌓인다.**
U6는 만성 열위라 계층 B 판정이 4년 중 상당 기간 참이므로, 병합 없이는 수백 건이 된다.

규약 (erd.md의 anomaly_event를 따른다):
- 연속으로 판정된 구간을 **하나의 이벤트**로 합친다
- 구간이 데이터 끝까지 이어지면 `end_time = NaT` = **진행 중** (erd.md:134)
- 등급이 있는 계층(B)은 구간 내 **도달한 최고 등급**을 event_type으로 삼는다.
  선별 구간과 확정 구간을 별도 이벤트로 만들면 같은 사건이 둘로 쪼개진다
- 멱등성 키 `(farm_code, turbine_code, tier, event_type, start_time)` —
  같은 구간을 다시 계산해도 중복 생성되지 않게 하는 근거 (DB UNIQUE 대상)
"""
import numpy as np
import pandas as pd

TS = "일자"
UNIT = "발전호기"

# anomaly_event 확정 스키마와 일치. (priority_rank 는 내부 계산값 — CSV/DB 내보낼 때
# 스키마 컬럼만 select. stop_rate·data_presence_rate·status·dismissed_reason·
# extrapolated_share 는 스키마 확정에서 제거됨. z_score는 계층 B만 채움, 계층 A는 NULL.)
EVENT_COLUMNS = [
    "farm_code", "turbine_code", "tier", "event_type",
    "start_time", "end_time", "duration_hours",
    "z_score", "expected_power", "actual_power", "deviation_pct",
    "energy_ratio_30d", "estimated_loss_kwh", "scope",
    "priority_rank",
]


def find_runs(mask: pd.Series) -> list[tuple[int, int]]:
    """True 연속 구간의 (시작 위치, 끝 위치) 목록. 위치는 0-based, 끝은 포함.

    행 위치 기준(1행=1스텝 가정). 계층 B의 일 단위 시계열처럼 격자가 촘촘한 곳에서 쓴다.
    시간 갭이 있는 원자료(계층 A)에는 find_time_runs를 쓴다.
    """
    v = mask.fillna(False).to_numpy(dtype=bool)
    if not v.any():
        return []
    edges = np.diff(np.concatenate(([0], v.view(np.int8), [0])))
    starts = np.flatnonzero(edges == 1)
    ends = np.flatnonzero(edges == -1) - 1
    return list(zip(starts.tolist(), ends.tolist()))


def find_time_runs(times, mask: pd.Series) -> list[tuple[int, int]]:
    """mask가 True이면서 **시간 연속(직전 행과 1시간)**인 run의 (시작,끝) 행위치.

    원자료는 빈 시간을 행으로 채우지 않으므로(격자 없음), 정지 run이 데이터 갭을
    건너뛰지 않게 시각으로 끊는다. 갭 중엔 정지였는지 알 수 없어 별개 사건이다.
    """
    v = mask.fillna(False).to_numpy(dtype=bool)
    n = len(v)
    if n == 0 or not v.any():
        return []
    t = pd.to_datetime(pd.Series(times).values)
    gap = np.zeros(n, dtype=bool)
    if n > 1:
        dmin = (np.diff(t) / np.timedelta64(1, "m")).astype(float)
        gap[1:] = dmin > 60.0
    runs, i = [], 0
    while i < n:
        if not v[i]:
            i += 1
            continue
        start = i
        i += 1
        while i < n and v[i] and not gap[i]:
            i += 1
        runs.append((start, i - 1))
    return runs


def find_gaps(times, min_hours: int) -> list[tuple]:
    """인접 시각 간 **빈 시간 수 ≥ min_hours**인 결측 구간 목록.

    반환: (첫 결측시각, 끝 결측시각, 결측시간수). 데이터 행이 없는 것 자체가 부재 신호다.
    마지막 관측 이후는 감지하지 않는다(호기별 관측 [처음,끝] 안의 갭만 — 과거 완전격자가
    호기별 [처음,끝]만 채웠던 것과 동일 범위).
    """
    t = pd.to_datetime(pd.Series(times).values)
    out = []
    for i in range(1, len(t)):
        missing = int(round((t[i] - t[i - 1]) / np.timedelta64(1, "h"))) - 1
        if missing >= min_hours:
            out.append((
                pd.Timestamp(t[i - 1]) + pd.Timedelta("1h"),
                pd.Timestamp(t[i]) - pd.Timedelta("1h"),
                missing,
            ))
    return out


def turbine_code(unit) -> str:
    """SCADA 발전호기(정수) → erd.md turbine.turbine_code 표기."""
    return f"U{int(unit)}"


def empty_events() -> pd.DataFrame:
    return pd.DataFrame(columns=EVENT_COLUMNS)


def finalize(events: list[dict]) -> pd.DataFrame:
    """이벤트 목록 → 표준 스키마 DataFrame. 최근 시작 순으로 정렬."""
    if not events:
        return empty_events()
    df = pd.DataFrame(events)
    for c in EVENT_COLUMNS:
        if c not in df.columns:
            df[c] = pd.NA
    return df[EVENT_COLUMNS].sort_values("start_time", ascending=False).reset_index(drop=True)


def assign_priority(events: pd.DataFrame, severity_order: dict) -> pd.DataFrame:
    """우선순위 부여 — 등급 먼저, 같은 등급 안에서 손실 발전량 내림차순.

    게이트를 통과한 이벤트만 들어온다는 전제다. 유의하지 않은 호기는 손실 kWh가
    아무리 커도 여기까지 오면 안 된다 (큰 절대 손실이 노이즈 범위 안일 수 있다).
    """
    if events.empty:
        return events
    out = events.copy()
    out["_sev"] = out["event_type"].map(severity_order).fillna(99)
    out = out.sort_values(["_sev", "estimated_loss_kwh"], ascending=[True, False])
    out["priority_rank"] = range(1, len(out) + 1)
    return out.drop(columns=["_sev"]).reset_index(drop=True)
