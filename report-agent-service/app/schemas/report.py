"""보고서 API 요청/응답 스키마 (공유 — 전 report_type 공용)."""
from typing import Optional, List

from pydantic import BaseModel, Field

from app.agents.registry import EVENT_ID_MEANING


class ReportRequest(BaseModel):
    report_type: str = Field(
        ..., examples=["operation"],
        description="anomaly | defect | operation | farm_operation",
    )
    event_id: int = Field(
        ...,
        examples=[2],
        # 유형별 의미는 EVENT_ID_MEANING(registry) 단일 기준을 그대로 인용한다 — 계약이 한 곳에서만 바뀌게.
        description="트리거 식별자 — report_type 마다 의미가 다르다. "
        + " · ".join(f"{t}={m}" for t, m in EVENT_ID_MEANING.items()),
    )
    period_start: Optional[str] = Field(
        None, examples=["2025-03-01"],
        description="조회 기간 시작(YYYY-MM-DD). operation/farm_operation에서만 사용. "
                    "미지정 시 대상의 관측 전 구간.",
    )
    period_end: Optional[str] = Field(
        None, examples=["2025-05-31"],
        description="조회 기간 종료(YYYY-MM-DD, 해당 일 포함). operation/farm_operation에서만 사용.",
    )

    def to_params(self) -> Optional[dict]:
        """service.generate_report(params=...)로 넘길 부가 파라미터."""
        p = {}
        if self.period_start:
            p["period_start"] = self.period_start
        if self.period_end:
            p["period_end"] = self.period_end
        return p or None


class ReportResponse(BaseModel):
    report_type: str
    event_id: int
    params: Optional[dict] = None   # 실제 적용된 조회 파라미터(기간 등)
    found: bool
    verdict: Optional[str] = None
    retry_count: int = 0
    issues: List[str] = []
    warnings: List[str] = []
    error: Optional[str] = None
    title: Optional[str] = None     # 보고서 제목 (Report.title 저장용, 200자 이내)
    context: Optional[str] = None   # 보고서 본문 마크다운 (제목 H1 제외, Report.context 저장용)
