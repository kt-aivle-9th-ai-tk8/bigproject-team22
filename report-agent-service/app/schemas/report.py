"""보고서 API 요청/응답 스키마 (공유 — 전 report_type 공용)."""
from typing import Optional, List

from pydantic import BaseModel, Field


class ReportRequest(BaseModel):
    report_type: str = Field(
        ..., examples=["operation"],
        description="anomaly | defect | operation | farm_operation",
    )
    event_id: int = Field(
        ...,
        examples=[2],
        description=(
            "트리거 식별자(유형별 의미 상이). "
            "anomaly/defect=event_id · operation=turbine_id(전역 유일) · "
            "farm_operation=wind_farm_id"
        ),
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
    draft: Optional[str] = None   # 마크다운 보고서 본문
