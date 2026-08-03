"""supervisor_node — 흐름 라우팅(공유) + 재시도 정책(per-type).

라우팅(유형 무관, 공유):
  tool_outputs 없음   → fetch
  draft 없음          → {report_type}_agent
  critic_result 없음  → critic
  critic 이후         → REGISTRY[report_type]["retry_policy"](state)  (기본 = _default_retry_policy)

기본 정책 `_default_retry_policy` (유형 중립 — verdict/issues만 읽는다):
  verdict == "적합"    → end
  부적합 + 여력 남음   → {report_type}_agent (재작성, retry_feedback = issues)
  부적합 + 재시도 소진 → end (부적합인 채 종료)

이 기본 정책은 어떤 critic_result든 `verdict`/`issues`만 보므로 특정 유형(anomaly)에 종속되지 않는다.
hard/soft 소진·강등처럼 유형별 규칙이 필요하면 REGISTRY에 `retry_policy`를 주입해 override한다
(예: reports/anomaly/critic_rules.retry_policy — 환각(hard)은 통과 불가, soft만 남으면 경고 강등).
`max_retries`도 per-type (각 타입이 REGISTRY에서 소유, service가 state에 시드). → supervisor 포크 불필요.
"""
from app.agents.registry import REGISTRY


def _default_retry_policy(state) -> dict:
    """유형 중립 기본 정책 — critic_result의 verdict/issues만 읽는다.

    "적합"이면 종료, 아니면 여력이 있을 때 재작성(issues를 피드백으로), 소진되면 부적합인 채 종료.
    hard/soft 같은 유형 고유 규칙은 각 타입이 자기 retry_policy로 소유한다(이 함수는 그 어휘를 모른다).
    """
    cr = state["critic_result"]
    if cr.get("verdict") == "적합":
        return {"next_agent": "end"}

    retry = state.get("retry_count", 0)
    if retry < state["max_retries"]:
        return {
            "next_agent": f"{state['report_type']}_agent",
            "critic_result": None,
            "retry_feedback": cr.get("issues", []),
            "retry_count": retry + 1,
        }
    return {"next_agent": "end"}  # 재시도 소진 → 부적합인 채 종료


def supervisor_node(state):
    report_type = state["report_type"]

    if state.get("tool_outputs") is None:
        return {"next_agent": "fetch"}
    if state.get("draft") is None:
        return {"next_agent": f"{report_type}_agent"}
    if state.get("critic_result") is None:
        return {"next_agent": "critic"}

    # critic 이후: per-type 정책(있으면) 또는 유형 중립 기본 정책.
    policy = REGISTRY[report_type].get("retry_policy") or _default_retry_policy
    return policy(state)
