"""보고서 API 라우트 (공유 — main.py가 /api/v1/reports 로 마운트).

제네릭 엔드포인트: report_type + event_id(+선택 기간)로 전 타입 생성.
  POST /api/v1/reports
      {"report_type": "operation", "event_id": 2,
       "period_start": "2025-03-01", "period_end": "2025-05-31"}

event_id 의미 (유형별):
  anomaly/defect  = event_id
  operation       = turbine_id   ← 전역 유일값이라 단지 구분 없이 정확히 식별
  farm_operation  = wind_farm_id
기간(period_*)은 operation/farm_operation에서만 사용하며, 미지정 시 관측 전 구간.
"""
from typing import Optional

from fastapi import APIRouter, HTTPException, Query

from app.agents.graph import REPORT_TYPES
from app.service import generate_report
from app.schemas.report import ReportRequest, ReportResponse

router = APIRouter()


def _run(report_type: str, event_id: int, params: Optional[dict] = None) -> dict:
    if report_type not in REPORT_TYPES:
        raise HTTPException(status_code=422, detail=f"알 수 없는 report_type: {report_type}")
    res = generate_report(report_type, event_id, params)
    if res.get("error"):
        raise HTTPException(status_code=502, detail=res["error"])   # LLM 재시도 소진 등
    if not res["found"]:
        raise HTTPException(
            status_code=404,
            detail=f"대상 없음: {report_type}/{event_id}"
                   + (f" (기간 {params})" if params else ""),
        )
    return res


@router.post("", response_model=ReportResponse)
def create_report(req: ReportRequest):
    """보고서 생성 (제네릭). operation=turbine_id, farm_operation=wind_farm_id."""
    return _run(req.report_type, req.event_id, req.to_params())


@router.post("/operation/{turbine_id}", response_model=ReportResponse)
def create_operation_report(
    turbine_id: int,
    period_start: Optional[str] = Query(None, examples=["2025-03-01"]),
    period_end: Optional[str] = Query(None, examples=["2025-05-31"]),
):
    """터빈 운영 보고서 — turbine_id(전역 유일)로 호출. 기간은 선택."""
    params = {k: v for k, v in
              (("period_start", period_start), ("period_end", period_end)) if v}
    return _run("operation", turbine_id, params or None)


@router.post("/farm-operation/{wind_farm_id}", response_model=ReportResponse)
def create_farm_operation_report(
    wind_farm_id: int,
    period_start: Optional[str] = Query(None, examples=["2025-03-01"]),
    period_end: Optional[str] = Query(None, examples=["2025-05-31"]),
):
    """단지 운영 보고서 — wind_farm_id로 호출. 기간은 선택."""
    params = {k: v for k, v in
              (("period_start", period_start), ("period_end", period_end)) if v}
    return _run("farm_operation", wind_farm_id, params or None)
