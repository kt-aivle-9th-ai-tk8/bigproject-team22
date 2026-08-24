"""보고서 API 라우트 (공유 — main.py가 /api-internal/reports 로 마운트).

입구는 제네릭 하나뿐이다: report_type + event_id(+선택 기간)로 4종 전부 생성.
  POST /api-internal/reports
      {"report_type": "operation", "event_id": 11,
       "period_start": "2025-03-01", "period_end": "2025-05-31"}

유형별 편의 경로(/operation/{id} 등)는 두지 않는다 — 호출자는 제네릭 하나만 쓰므로
입구가 늘면 event_id 의미·기간 파라미터·에러 응답 계약만 두 벌이 된다.

event_id 의미 (유형별): registry.EVENT_ID_MEANING 이 단일 기준.
  anomaly/defect  = event_id
  operation       = turbine_id   ← 전역 유일값이라 단지 구분 없이 정확히 식별
  farm_operation  = wind_farm_id
기간(period_*)은 operation/farm_operation에서만 사용하며, 미지정 시 관측 전 구간.
"""
import threading
from typing import Optional

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


def _run(report_type: str, event_id: int, params: Optional[dict] = None) -> dict:
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
        res = generate_report(report_type, event_id, params)
    finally:
        _slots.release()

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
    """보고서 생성 (제네릭 — 4종 공통 입구). event_id 의미는 registry.EVENT_ID_MEANING 단일 기준
    (anomaly=event_id · defect=report_id · operation=turbine_id · farm_operation=wind_farm_id)."""
    return _run(req.report_type, req.event_id, req.to_params())
