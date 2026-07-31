"""공유 critic 노드 — report_type별 critic 콜러블로 dispatch만 한다(계약-구동).

REGISTRY[report_type]["critic"]:
    callable(state) -> critic_result   # 유형이 소유하는 검증 (무엇을 검사할지 자유)
    None                               # 검증 스킵 → 중립 통과 결과(_PASS)

critic_result의 최소 계약은 기본 정책(_default_retry_policy)이 읽는 `verdict`("적합"이면 통과)
+ `issues`(재작성 피드백)뿐이다. 그 외 필드(유형별 hard/soft/warnings 등)는 각 report_type의
critic과 그 retry_policy가 자유롭게 정한다(예: reports/anomaly/critic_rules).
"""
from app.agents.registry import REGISTRY

# critic이 없을 때의 중립 통과 결과. 기본 정책이 verdict만 보므로 이 두 키면 충분.
_PASS = {"verdict": "적합", "issues": []}


def critic_node(state):
    critic = REGISTRY[state["report_type"]].get("critic")
    return {"critic_result": critic(state) if critic else dict(_PASS)}
