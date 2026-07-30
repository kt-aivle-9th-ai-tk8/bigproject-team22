"""operation critic — REGISTRY["operation"]["critic"] 진입점 `critic(state)` + `retry_policy`.

anomaly와 동일 철학·구조(defense-in-depth + hard/soft 강등):
  hard = 총평에 숫자가 있으면 반려(수치는 표·차트 전용) — 결정론, 통과 불가.
  soft = 인과 단정 / 정비 지시 — 결정론 키워드 사전필터로 후보만 골라 LLM 정밀 판정(과탐 방지).
narrative가 없으면(WITH_ANALYSIS off) 통과.

retry_policy: hard 남으면 끝까지 부적합. soft만 남고 재시도 소진 시 경고로 강등해 '적합'(사람 확인).
"""
from pydantic import BaseModel, Field

from app.agents.llm import llm
from app.agents.verify import extract_numbers


def _hard_soft(hard, soft) -> dict:
    hard, soft = list(hard), list(soft)
    issues = hard + soft
    return {
        "verdict": "부적합" if issues else "적합",
        "issues": issues,
        "hard_issues": hard,
        "soft_issues": soft,
    }


def critic(state) -> dict:
    """운영 총평 검증. narrative 없으면 통과, 있으면 hard/soft 분류."""
    analysis = state.get("narrative")
    if not analysis:
        return _hard_soft([], [])
    return _hard_soft(_code_checks(analysis), _llm_check(analysis))


def retry_policy(state) -> dict:
    """hard/soft 소진 정책 (anomaly와 동일 구조)."""
    report_type = state["report_type"]
    cr = state["critic_result"]
    hard = cr.get("hard_issues", [])
    soft = cr.get("soft_issues", [])
    retry = state.get("retry_count", 0)
    max_r = state["max_retries"]

    if not hard and not soft:
        return {"next_agent": "end"}
    if retry < max_r:
        return {
            "next_agent": f"{report_type}_agent",
            "critic_result": None,
            "retry_feedback": hard + soft,
            "retry_count": retry + 1,
        }
    if hard:
        return {"next_agent": "end"}  # 숫자 위반 남음 → 부적합 유지
    return {  # soft만 남음 → 경고로 강등해 통과
        "next_agent": "end",
        "critic_result": {**cr, "verdict": "적합", "issues": [], "warnings": soft},
    }


# ── hard: 총평에 숫자 금지 (모든 수치는 표·차트 소유) ────────────────────────
def _code_checks(analysis: str) -> list:
    nums = extract_numbers(analysis)
    if nums:
        return [f"총평에 숫자 사용({[round(n, 4) for n in nums]}) — 수치는 표·차트에만. "
                "숫자를 빼고 정성 표현으로 재작성하라."]
    return []


# ── soft: 인과 단정 / 정비 지시 (키워드 사전필터 → LLM 정밀) ──────────────────
# 원인-메커니즘/설비 명사(1차, 高recall). 없으면 LLM 인과 판정 스킵.
_CAUSE_MECHANISMS = ("고장", "결함", "파손", "베어링", "기어", "블레이드", "낙뢰",
                     "과열", "누유", "마모", "균열", "단락", "부품")
# 구체적 정비 동작(1차). '점검·모니터링·확인'은 정당한 운영 권고라 넣지 않는다.
_REPAIR_WORDS = ("교체", "수리", "분해", "재조립", "정비")


def _llm_check(analysis: str) -> list:
    cause_cand = any(w in analysis for w in _CAUSE_MECHANISMS)
    repair_cand = any(w in analysis for w in _REPAIR_WORDS)
    if not cause_cand and not repair_cand:
        return []   # 후보 없음 → LLM 미호출 (대부분 여기서 통과, retry 0)

    res = _critic_llm.invoke(
        "다음은 '터빈 운영 리포트'의 운영 총평이다. 기준은 스키마 필드 설명을 따른다.\n\n"
        f"[총평]\n{analysis}"
    )
    issues = []
    if cause_cand and res.causal_assertion:
        issues.append("인과 단정 표현 — 완곡(가능성/확인 필요)으로 재작성")
    if repair_cand and res.repair_directive:
        issues.append("구체적 정비·수리 조치 지시 — 운영 관점 주의 환기로 재작성")
    return issues


class _CriticLLM(BaseModel):
    causal_assertion: bool = Field(
        description="총평이 이상/저하가 '왜' 일어났는지 그 근본 원인을 특정 원인(부품 고장·기상 등)으로 "
        "단정하면 true. 반드시 false: 지표 서술('이용률/가동률이 낮다'), 손실 귀속('정지 손실이 대부분'), "
        "상관·정황, 완곡('가능성이 있다/시사한다/확인이 필요하다')."
    )
    repair_directive: bool = Field(
        description="구체적 정비·수리·부품 교체를 지시하면 true(예: '베어링을 교체하라'). "
        "반드시 false: '점검/모니터링/확인/운영팀 확인이 필요하다' 같은 일반 운영 권고."
    )


_critic_llm = llm.with_structured_output(_CriticLLM)
