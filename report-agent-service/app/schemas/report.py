"""보고서 API 요청/응답 스키마 (공유 — 전 report_type 공용)."""
from typing import Optional, List

from pydantic import BaseModel, Field


class ReportRequest(BaseModel):
    report_type: str = Field(..., examples=["operation"], description="anomaly | defect | operation | farm_operation")
    event_id: int = Field(
        ...,
        examples=[2],
        description="트리거 식별자. anomaly/defect=event_id, operation=터빈 번호(2→U2).",
    )


class ReportResponse(BaseModel):
    report_type: str
    event_id: int
    found: bool
    verdict: Optional[str] = None
    retry_count: int = 0
    issues: List[str] = []
    warnings: List[str] = []
    error: Optional[str] = None
    draft: Optional[str] = None   # 마크다운 보고서 본문
