"""보고서 생성 서비스 — CLI(run.py)와 후속 API(api.py)가 공유하는 진입 함수.

그래프 밖에서 순수 dict를 반환한다(저장은 호출자가: CLI→파일, API→RDS/S3).
"""
from app.agents.graph import build_app, REPORT_TYPES
from app.agents.registry import REGISTRY

# Report.title 컬럼 상한(VARCHAR(200)). 제목 규격이 짧아 실제로 잘릴 일은 거의 없지만 BE 저장 보장용.
TITLE_MAX = 200


def _split_title_context(draft: str):
    """draft(전체 마크다운) → (title, context). Report.title / Report.context 에 그대로 저장 가능.

    4종 builder 모두 본문을 '# {제목}'(H1)으로 시작한다 — 그 H1 이 제목의 단일 출처다.
    별도 title 함수를 두면 포맷이 render 인라인과 이중화되므로, 여기서 그 H1 을 그대로 읽는다.
      title   : 첫 줄 H1 에서 '#' 를 뗀 제목. Report.title(VARCHAR(200)) 저장용으로 TITLE_MAX 컷.
      context : 제목 H1 을 뺀 본문. title 과 중복되지 않아 두 컬럼에 바로 넣을 수 있다.
    """
    if not draft:
        return None, None
    head, _, rest = draft.partition("\n")
    title = head.strip().lstrip("#").strip()[:TITLE_MAX] or None
    context = rest.lstrip("\n") or None
    return title, context


def _init_state(report_type: str, event_id: int, params: dict = None) -> dict:
    return {
        "event_id": event_id,
        "params": dict(params) if params else None,
        "report_type": report_type,
        "tool_outputs": None,
        "narrative": None,
        "draft": None,
        "critic_result": None,
        "retry_feedback": None,
        "retry_count": 0,
        # per-type 재시도 상한 — 필수(각 타입이 REGISTRY에서 소유). 누락 시 KeyError로 즉시 드러남.
        "max_retries": REGISTRY[report_type]["max_retries"],
        "next_agent": None,
    }


def _error_result(report_type: str, event_id: int, error: str) -> dict:
    """생성 실패 시의 표준 결과(성공 결과와 같은 키 + error)."""
    return {
        "report_type": report_type,
        "event_id": event_id,
        "found": False,
        "title": None,
        "context": None,
        "verdict": None,
        "retry_count": 0,
        "issues": [],
        "warnings": [],
        "error": error,
    }


def generate_report(report_type: str, event_id: int, params: dict = None) -> dict:
    """report_type·event_id(+선택 params)로 보고서 생성 → 결과 dict.

    params: 유형별 부가 조회 파라미터(선택). 미지정 시 각 유형의 기본 동작.
      - operation/farm_operation: {"period_start": "YYYY-MM-DD", "period_end": "YYYY-MM-DD"}
        (미지정 시 대상의 관측 전 구간)
      - anomaly/defect: 사용하지 않음(이벤트 자체가 기간을 규정)

    반환: {report_type, event_id, params, found, title, context, verdict, retry_count, issues, warnings, error}
      title/context 는 draft(전체 마크다운)를 제목 H1 기준으로 쪼갠 것 — Report.title/Report.context 대응.
    error 는 정상 시 None, LLM 호출이 재시도 소진 후에도 실패하면 사유 문자열.
    warnings 는 재시도 소진 후 soft 이슈만 남아 '적합'으로 강등된 경우의 지적(사람 확인용).
    """
    if report_type not in REPORT_TYPES:
        raise ValueError(f"알 수 없는 report_type: {report_type} (가능: {REPORT_TYPES})")

    app = build_app()
    # config: 트레이싱 시 LangSmith에서 run_name/tag/metadata로 검색·필터 가능하게 태깅.
    #   (트레이싱이 꺼져 있어도 langgraph는 config를 무시할 뿐이라 무해.)
    run_config = {
        "run_name": f"{report_type}:{event_id}",
        "tags": [report_type],
        "metadata": {"report_type": report_type, "event_id": event_id, "params": params or {}},
    }
    try:
        final = app.invoke(_init_state(report_type, event_id, params), config=run_config)
    except Exception as e:
        # llm.py의 재시도가 소진된 뒤의 최종 실패(쿼터/네트워크/타임아웃 등).
        # 호출자(CLI/API)에 트레이스백 대신 처리 가능한 결과를 돌려준다.
        return _error_result(report_type, event_id, f"{type(e).__name__}: {e}")

    event = (final.get("tool_outputs") or {}).get("event", {})
    critic = final.get("critic_result") or {}
    title, context = _split_title_context(final.get("draft"))
    return {
        "report_type": report_type,
        "event_id": event_id,
        "params": params or None,
        "found": event.get("found", False),
        "title": title,
        "context": context,
        "verdict": critic.get("verdict"),
        "retry_count": final.get("retry_count", 0),
        "issues": critic.get("issues", []),
        "warnings": critic.get("warnings", []),
        "error": None,
    }
