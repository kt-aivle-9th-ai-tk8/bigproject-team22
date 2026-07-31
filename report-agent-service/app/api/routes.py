"""보고서 API 라우트 (공유 — main.py가 /api/v1/reports 로 마운트).

제네릭 엔드포인트: report_type + event_id로 전 타입(anomaly/defect/operation/farm_operation) 생성.
  POST /api/v1/reports        {report_type, event_id}
  POST /api/v1/reports/operation/{turbine_code}   (편의 — U2 → event_id 2)
"""
import re

from fastapi import APIRouter, HTTPException

from app.agents.graph import REPORT_TYPES
from app.service import generate_report
from app.schemas.report import ReportRequest, ReportResponse

router = APIRouter()


def _run(report_type: str, event_id: int) -> dict:
    if report_type not in REPORT_TYPES:
        raise HTTPException(status_code=422, detail=f"알 수 없는 report_type: {report_type}")
    res = generate_report(report_type, event_id)
    if res.get("error"):
        raise HTTPException(status_code=502, detail=res["error"])   # LLM 재시도 소진 등
    if not res["found"]:
        raise HTTPException(status_code=404, detail=f"대상 없음: {report_type}/{event_id}")
    return res


@router.post("", response_model=ReportResponse)
def create_report(req: ReportRequest):
    """보고서 생성 (제네릭). operation은 event_id=터빈 번호(2→U2)."""
    return _run(req.report_type, req.event_id)


@router.post("/operation/{turbine_code}", response_model=ReportResponse)
def create_operation_report(turbine_code: str):
    """운영 보고서 편의 엔드포인트 — 터빈 코드(U2)로 호출."""
    m = re.fullmatch(r"[Uu](\d+)", turbine_code.strip())
    if not m:
        raise HTTPException(status_code=422, detail=f"터빈 코드 형식 오류: {turbine_code} (예: U2)")
    return _run("operation", int(m.group(1)))
