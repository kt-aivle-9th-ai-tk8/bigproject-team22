"""operation critic — grounding(hard) + causal/repair(soft). REGISTRY 진입점 critic/retry_policy.

팀 컨벤션(anomaly/defect와 동일 계열):
  hard = code_checks : 분석의 모든 숫자가 facts 허용집합(builder.allowed_numbers)에 있나
                       → 없으면 환각/변형 수치. 통과 불가(비-degradable).
  soft = llm_check   : 근본 원인 단정 / 정비 지시. 결정론 키워드 필터 → 걸린 것만 LLM 정밀 판정.
critic_result: {verdict, issues, hard_issues, soft_issues}. retry_policy가 hard/soft로 재작성 정책.
"""
from pydantic import BaseModel, Field

from app.agents.llm import llm
from app.agents.verify import extract_numbers
from app.agents.reports.operation import builder


def _tol(x):
    """허용 오차 — 1% 상대 또는 0.05 절대 중 큰 값(반올림 흡수). anomaly/defect와 동일."""
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


# 인과 단정 후보 메커니즘 명사 / 정비 동작 (1차 결정론 필터, 高recall).
_CAUSE_MECHANISMS = ("고장", "결함", "파손", "베어링", "기어", "블레이드", "낙뢰",
                     "과열", "누유", "마모", "균열", "단락", "부품")
_REPAIR_WORDS = ("교체", "수리", "분해", "재조립", "정비")


class _CriticLLM(BaseModel):
    causal_assertion: bool = Field(
        description="분석이 이상/저하가 '왜' 일어났는지 근본 원인을 특정 원인(부품 고장·기상 등)으로 단정하면 "
        "true. 지표 서술('가동률이 낮다')·손실 귀속('정지 손실이 대부분')·정황·완곡('가능성/확인 필요')은 false."
    )
    repair_directive: bool = Field(
        description="구체적 정비·수리·부품 교체를 지시하면 true(예: '베어링 교체하라'). "
        "'점검/모니터링/확인/운영팀 확인이 필요하다' 같은 일반 운영 권고는 false."
    )


_critic_llm = llm.with_structured_output(_CriticLLM)


def llm_check(state) -> list:
    """soft — 키워드 필터가 걸린 것만 LLM 판정. 깨끗하면 LLM 미호출."""
    narrative = state.get("narrative") or ""
    cause_cand = any(w in narrative for w in _CAUSE_MECHANISMS)
    repair_cand = any(w in narrative for w in _REPAIR_WORDS)
    if not cause_cand and not repair_cand:
        return []
    res = _critic_llm.invoke(
        f"다음 운영 총평의 근본원인 단정/정비지시 여부를 판정하라.\n\n[총평]\n{narrative}")
    issues = []
    if cause_cand and res.causal_assertion:
        issues.append("인과 단정 표현 → 가능성/정황/확인 필요로 완곡화하라.")
    if repair_cand and res.repair_directive:
        issues.append("구체적 정비·수리 조치 지시 → 운영 관점 주의 환기로 재작성하라.")
    return issues


def critic(state) -> dict:
    """REGISTRY["operation"]["critic"] 진입점. narrative 없으면 통과, 있으면 hard/soft 분류."""
    if not state.get("narrative"):
        return {"verdict": "적합", "issues": [], "hard_issues": [], "soft_issues": []}
    hard = code_checks(state)   # grounding
    soft = llm_check(state)     # causal/repair
    issues = hard + soft
    return {"verdict": "부적합" if issues else "적합", "issues": issues,
            "hard_issues": hard, "soft_issues": soft}


def retry_policy(state) -> dict:
    """hard(환각)는 통과 불가, soft(인과/정비)만 남으면 재시도 소진 시 경고 강등. anomaly와 동일."""
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
