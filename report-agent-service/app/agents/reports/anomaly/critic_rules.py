"""anomaly critic — grounding(hard) + causal(soft). REGISTRY 진입점 critic/retry_policy.

hard = code_checks : 분석의 모든 숫자가 facts 허용집합(builder.allowed_numbers)에 있나
                     → 없으면 환각/변형 수치. 통과 불가(비-degradable).
soft = llm_check   : 근본 원인 단정. 결정론 키워드 필터 → 걸린 것만 LLM 정밀 판정.
critic_result: {verdict, issues, hard_issues, soft_issues}. retry_policy가 hard/soft로 재작성 정책.

defect critic_rules와 같은 계열(code_checks/llm_check) — 팀 검증 규칙 컨벤션 공유.
"""
from pydantic import BaseModel, Field

from app.agents.llm import llm
from app.agents.verify import extract_numbers
from app.agents.reports.anomaly import builder


def _tol(x):
    """허용 오차 — 1% 상대 또는 0.05 절대 중 큰 값(큰 수치의 반올림 흡수). defect와 동일."""
    return max(abs(x) * 0.01, 0.05)


def code_checks(state) -> list:
    """grounding(hard) — 분석의 모든 숫자가 facts 허용집합에 있나. 없으면 환각/변형으로 반려."""
    narrative = state.get("narrative") or ""
    allowed = builder.allowed_numbers(state["tool_outputs"])
    bad = []
    for line in narrative.split("\n"):
        for n in extract_numbers(line):
            if not any(abs(abs(n) - a) <= _tol(a) for a in allowed):
                bad.append(f'facts에 없는 수치 인용(환각 후보): {n:g} ("{line.strip()[:30]}…")')
    return bad


# 인과 단정 후보 메커니즘 명사(1차 결정론 필터, 高recall).
_CAUSE_MECHANISMS = ("고장", "결함", "파손", "베어링", "기어", "블레이드", "낙뢰",
                     "과열", "누유", "마모", "균열", "단락", "부품")


class _CriticLLM(BaseModel):
    causal_assertion: bool = Field(
        description="분석이 '이상(정지/부재/저하)이 왜 일어났는지' 원인을 특정 원인(고장·부품 결함·기상 등)으로 "
        "단정하면 true. 결과 서술('정지로 인해 손실')·정황·완곡('가능성/시사')은 false."
    )
    causal_span: str = Field(default="", description="true면 원인을 단정한 문구를 원문에서 그대로 한 구절 인용. false면 빈 문자열.")


_critic_llm = llm.with_structured_output(_CriticLLM)


def llm_check(state) -> list:
    """causal(soft) — 결정론 키워드 필터가 걸린 것만 LLM 정밀 판정. 깨끗하면 LLM 미호출."""
    narrative = state.get("narrative") or ""
    if not any(w in narrative for w in _CAUSE_MECHANISMS):
        return []
    res = _critic_llm.invoke(f"다음 이상감지 종합분석의 근본 원인 단정 여부를 판정하라.\n\n[분석]\n{narrative}")
    if not res.causal_assertion:
        return []
    span = (getattr(res, "causal_span", "") or "").strip()
    return [f'인과 단정 의심: "{span}" → 근본 원인 단정을 피하고 가능성/정황으로 완곡화하라.' if span
            else "인과 단정 표현 사용 → 근본 원인 단정을 피하고 가능성/정황으로 완곡화하라."]


def critic(state) -> dict:
    """REGISTRY["anomaly"]["critic"] 진입점. narrative 없으면 통과, 있으면 hard/soft 분류."""
    if not state.get("narrative"):
        return {"verdict": "적합", "issues": [], "hard_issues": [], "soft_issues": []}
    hard = code_checks(state)   # grounding
    soft = llm_check(state)     # causal
    issues = hard + soft
    return {"verdict": "부적합" if issues else "적합", "issues": issues,
            "hard_issues": hard, "soft_issues": soft}


def retry_policy(state) -> dict:
    """anomaly 전용 재시도 정책 — hard(환각)는 통과 불가, soft(인과)만 남으면 소진 시 경고 강등."""
    report_type = state["report_type"]
    cr = state["critic_result"]
    hard, soft = cr.get("hard_issues", []), cr.get("soft_issues", [])
    retry, max_r = state.get("retry_count", 0), state["max_retries"]

    if not hard and not soft:
        return {"next_agent": "end"}
    if retry < max_r:
        return {"next_agent": f"{report_type}_agent", "critic_result": None,
                "retry_feedback": hard + soft, "retry_count": retry + 1}
    if hard:
        return {"next_agent": "end"}  # 환각 수치 남음 → 부적합 유지
    return {"next_agent": "end",       # soft만 남음 → 경고로 강등해 통과
            "critic_result": {**cr, "verdict": "적합", "issues": [], "warnings": soft}}
