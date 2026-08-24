"""단일 supervisor 그래프 조립. report_type별 모듈 dispatch는 REGISTRY(app.agents.registry).

노드: supervisor, fetch, {anomaly,defect,operation}_agent, critic.
- fetch/critic 는 제네릭 노드. REGISTRY로 report_type별 모듈을 찾아 실행.
- REGISTRY/REPORT_TYPES는 app.agents.registry에 정의 — graph·critic 양쪽이 top-level import
  하도록 분리(과거의 순환 import 우회 제거). 여기서 재노출해 기존 `from app.agents.graph import ...` 유지.
build_app()은 1회 컴파일 후 캐시(요청마다 재컴파일 방지 → FastAPI에서 재사용).
"""
from langgraph.graph import StateGraph, END

from app.agents.state import ReportState
from app.agents.supervisor_agent import supervisor_node
from app.agents.critic_agent import critic_node
from app.agents.registry import REGISTRY, REPORT_TYPES
from app.core.datasource import snapshot


def fetch_node(state):
    """report_type별 fetch로 tool_outputs 채움 (LLM 아님, 검증 기준 수치).

    계약: fetch(event_id) 필수. params(기간 등)를 쓰는 유형은 fetch(event_id, params)로
    선언하면 함께 전달된다 — 선언하지 않은 fetch는 기존대로 호출되므로 하위호환.

    DB 조회는 이 노드에서만 일어난다(이후 agent/critic 은 tool_outputs 만 본다).
    그래서 스냅샷 트랜잭션도 여기서만 연다 — invoke 전체를 감싸면 LLM 호출·재시도 동안
    커넥션과 읽기 뷰가 수십 초씩 잡혀 풀 고갈·undo 누적을 부른다(#89 리뷰).
    """
    import inspect

    report_type = state["report_type"]
    fetch = REGISTRY[report_type]["fetch"]
    try:
        accepts_params = len(inspect.signature(fetch).parameters) >= 2
    except (TypeError, ValueError):   # 시그니처 조회 불가(C 확장 등) → 기본 호출
        accepts_params = False

    with snapshot():
        if accepts_params:
            return {"tool_outputs": fetch(state["event_id"], state.get("params"))}
        return {"tool_outputs": fetch(state["event_id"])}


_app = None


def build_app():
    """그래프 컴파일 (1회 캐시)."""
    global _app
    if _app is not None:
        return _app

    workflow = StateGraph(ReportState)
    workflow.add_node("supervisor", supervisor_node)
    workflow.add_node("fetch", fetch_node)
    workflow.add_node("critic", critic_node)
    for rt in REPORT_TYPES:
        workflow.add_node(f"{rt}_agent", REGISTRY[rt]["agent"])

    workflow.set_entry_point("supervisor")

    routes = {"fetch": "fetch", "critic": "critic", "end": END}
    for rt in REPORT_TYPES:
        routes[f"{rt}_agent"] = f"{rt}_agent"
    workflow.add_conditional_edges("supervisor", lambda s: s["next_agent"], routes)

    for node in ["fetch", "critic", *(f"{rt}_agent" for rt in REPORT_TYPES)]:
        workflow.add_edge(node, "supervisor")

    _app = workflow.compile()
    return _app
