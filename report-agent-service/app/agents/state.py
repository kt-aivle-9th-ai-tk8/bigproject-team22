"""ReportState 정의 — 그래프 전역 상태 (TypedDict). 3개 보고서 타입 공용."""
from typing import TypedDict, Optional


class ReportState(TypedDict):
    event_id: int
    report_type: str                # "anomaly" | "defect" | "operation" — 분기 기준
    tool_outputs: Optional[dict]    # fetch 결과 (본문 수치의 출처)
    narrative: Optional[str]        # LLM 분석 (facts 근거 정성 서술)
    draft: Optional[str]            # 조립된 보고서 (코드 주입 수치 + 분석)
    critic_result: Optional[dict]   # 최소 {verdict[, issues]}. 유형별 추가 필드 자유(anomaly: hard/soft/warnings)
    retry_feedback: Optional[list]  # 재작성 시 agent에 전달할 critic 지적
    retry_count: int
    max_retries: int
    next_agent: Optional[str]
