"""보고서 API 라우트 (공유 — main.py가 /api/v1/reports 로 마운트).

제네릭 엔드포인트: report_type + event_id로 전 타입(anomaly/defect/operation/farm_operation) 생성.
  POST /api/v1/reports        {report_type, event_id}
  POST /api/v1/reports/operation/{turbine_code}   (편의 — U2 → event_id 2)
"""
import re
import threading

from fastapi import APIRouter, HTTPException

from app.agents.graph import REPORT_TYPES
from app.core.config import MAX_CONCURRENT_REPORTS
from app.service import generate_report
from app.schemas.report import ReportRequest, ReportResponse

router = APIRouter()

# 동시 생성 상한. 보고서 1건이 LLM 을 여러 번(분석 + critic + 재시도) 부르므로 상한이 없으면
# 반복 호출이 그대로 병렬 LLM 호출이 된다(비용·쿼터·스레드풀 고갈).
# FastAPI 는 def 핸들러를 스레드풀에서 실행하므로 asyncio 가 아닌 threading 세마포어를 쓴다.
# 이건 인스턴스 1대 기준의 방어선이다 — 전체 호출량 제한은 게이트웨이/미들웨어 몫.
_slots = threading.BoundedSemaphore(MAX_CONCURRENT_REPORTS)


def _run(report_type: str, event_id: int) -> dict:
    if report_type not in REPORT_TYPES:
        raise HTTPException(status_code=422, detail=f"알 수 없는 report_type: {report_type}")

    # blocking=False — 생성이 수십 초라 대기시키면 요청만 쌓이고 워커 스레드가 잠긴다. 빨리 거절한다.
    if not _slots.acquire(blocking=False):
        raise HTTPException(
            status_code=429,
            detail=f"보고서 생성이 이미 {MAX_CONCURRENT_REPORTS}건 진행 중입니다. 잠시 후 다시 시도하세요.",
            headers={"Retry-After": "30"},
        )
    try:
        res = generate_report(report_type, event_id)
    finally:
        _slots.release()

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
