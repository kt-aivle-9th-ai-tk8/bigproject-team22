"""ReportState 정의 — 그래프 전역 상태 (TypedDict). 보고서 타입 공용."""
from typing import TypedDict, Optional


class ReportState(TypedDict):
    event_id: int                   # 트리거 식별자(유형별 의미 상이 — 아래 params 참고)
    params: Optional[dict]          # 부가 조회 파라미터(선택). 없으면 유형별 기본값.
                                    #   operation/farm_operation: {"period_start": "YYYY-MM-DD",
                                    #                              "period_end": "YYYY-MM-DD"}
                                    #   미지정 시 대상의 관측 전 구간을 사용한다.
    report_type: str                # "anomaly" | "defect" | "operation" — 분기 기준
    tool_outputs: Optional[dict]    # fetch 결과 (본문 수치의 출처)
    narrative: Optional[str]        # LLM 분석 (facts 근거 정성 서술)
    draft: Optional[str]            # 조립된 보고서 (코드 주입 수치 + 분석)
    critic_result: Optional[dict]   # 최소 {verdict[, issues]}. 유형별 추가 필드 자유(anomaly: hard/soft/warnings)
    retry_feedback: Optional[list]  # 재작성 시 agent에 전달할 critic 지적
    retry_count: int
    max_retries: int
    next_agent: Optional[str]
