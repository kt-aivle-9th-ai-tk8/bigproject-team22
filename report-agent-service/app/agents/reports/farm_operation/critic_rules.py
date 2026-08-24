"""farm_operation critic — grounding(hard) + causal/repair(soft).

operation critic과 동일 구조지만 grounding은 '단지 builder'의 allowed_numbers로 검증해야 한다
(각 report_type의 critic은 자기 builder를 참조하는 팀 컨벤션). soft·retry_policy는 operation 재사용.
"""
from app.agents.verify import extract_numbers
from app.agents.reports.farm_operation import builder
from app.agents.reports.operation.critic_rules import _tol, llm_check, retry_policy  # noqa: F401


def code_checks(state) -> list:
    """grounding(hard) — 분석의 모든 숫자가 단지 facts 허용집합에 있나."""
    narrative = state.get("narrative") or ""
    allowed = builder.allowed_numbers(state["tool_outputs"])
    bad = []
    for line in narrative.split("\n"):
        for n in extract_numbers(line):
            if not any(abs(abs(n) - a) <= _tol(a) for a in allowed):
                bad.append(f'facts에 없는 수치 인용(환각 후보): {n:g} ("{line.strip()[:30]}…")')
    return bad


def critic(state) -> dict:
    """REGISTRY["farm_operation"]["critic"] 진입점. narrative 없으면 통과."""
    if not state.get("narrative"):
        return {"verdict": "적합", "issues": [], "hard_issues": [], "soft_issues": []}
    hard = code_checks(state)   # grounding (단지 builder)
    soft = llm_check(state)     # causal/repair (operation 재사용 — narrative만 봄)
    issues = hard + soft
    return {"verdict": "부적합" if issues else "적합", "issues": issues,
            "hard_issues": hard, "soft_issues": soft}
