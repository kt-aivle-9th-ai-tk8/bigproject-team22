"""보고서 API 요청/응답 스키마 (공유 — 전 report_type 공용)."""
from typing import Optional, List

from pydantic import BaseModel, Field

from app.agents.registry import EVENT_ID_MEANING


class ReportRequest(BaseModel):
    report_type: str = Field(
        ...,
        examples=["defect"],
        description="anomaly | defect | operation | farm_operation",
    )
    # 4개 타입이 fetch(event_id) 하나를 공유해서 이름은 event_id 지만 가리키는 대상은 타입마다 다르다.
    # 문구의 원본은 registry.EVENT_ID_MEANING — 고칠 일이 생기면 거기서 고친다.
    event_id: int = Field(
        ...,
        examples=[5001],
        description="대상 식별자 — report_type 마다 의미가 다르다. "
        + " / ".join(f"{t}: {m}" for t, m in EVENT_ID_MEANING.items()),
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
