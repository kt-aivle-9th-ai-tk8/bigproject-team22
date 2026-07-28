"""anomaly critic — REGISTRY["anomaly"]["critic"]로 주입되는 진입점 `critic(state)`.

진입점: `critic(state) -> critic_result` (narrative 없으면 통과, 아니면 hard/soft 분류).
  hard = code_checks  # 결정론: 분석에 숫자가 있으면 반려(수치는 표·차트 전용)
  soft = llm_check     # 레이어드: 결정론 키워드 필터 → 걸린 것만 LLM 정밀 판정
code_checks/llm_check/_causal_candidate/_CriticLLM 는 이제 anomaly 내부 helper(공개 계약 아님).

인과 가드레일은 실무 표준 defense-in-depth로 구성한다:
  1) 결정론 키워드 필터(_causal_candidate) = 싼 1차, 高recall — 원인-메커니즘 명사 등장 여부.
  2) LLM 정밀 판정(_critic_llm) = 걸린 것만, 高precision — 단정인지 완곡(가능성)인지 확정.
필터가 깨끗하면 LLM을 아예 호출하지 않는다 → 대부분 리포트는 인과 판정 미호출(retry 0).
"""
from pydantic import BaseModel, Field

from app.agents.llm import llm
from app.agents.verify import extract_numbers


def _hard_soft(hard, soft) -> dict:
    """anomaly critic_result 조립 — hard(치명)/soft(오탐 가능) 이슈로 나눠 담는다.

    hard/soft는 아래 retry_policy가 읽는다(환각=hard는 통과 불가, soft만 남으면 소진 시 경고 강등).
    이 hard/soft 어휘는 anomaly 고유다 — 다른 report_type은 이 모양을 따를 필요가 없다(자기 형태 자유).
    """
    hard, soft = list(hard), list(soft)
    issues = hard + soft
    return {
        "verdict": "부적합" if issues else "적합",
        "issues": issues,          # 기본/공유가 읽는 최소 계약(재작성 피드백)
        "hard_issues": hard,       # anomaly retry_policy 전용
        "soft_issues": soft,
    }


def critic(state) -> dict:
    """anomaly critic 진입점 (REGISTRY["anomaly"]["critic"]).

    narrative가 없으면(WITH_ANALYSIS off → 결정론 리포트) 검증 대상이 없어 통과.
    있으면 hard=숫자 게이트(code_checks) / soft=인과 레이어드 필터(llm_check)로 분류.
    """
    if not state.get("narrative"):
        return _hard_soft([], [])
    return _hard_soft(code_checks(state), llm_check(state))


def retry_policy(state) -> dict:
    """anomaly 전용 재시도 정책 (REGISTRY["anomaly"]["retry_policy"]) — hard/soft 소진 정책.

    이슈 없음→end / 여력 남으면→재작성(hard·soft 무엇이든 고칠 기회) / 재시도 소진 시:
      - hard(환각) 남으면 → end, 부적합 유지 (절대 통과 불가)
      - soft만 남으면    → end, 경고로 강등해 '적합'으로 통과 (사람 확인)
    공유 supervisor의 유형 중립 기본 정책 대신 이 정책이 REGISTRY 주입으로 쓰인다.
    """
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
        return {"next_agent": "end"}  # 환각 남음 → 부적합 유지
    return {
        "next_agent": "end",
        "critic_result": {**cr, "verdict": "적합", "issues": [], "warnings": soft},
    }

# 결정론 인과 필터(1차). '이상의 원인'으로 지목될 수 있는 메커니즘/설비 명사.
#   高recall 목적이라 완곡('고장 가능성')도 일단 잡고, 단정 여부는 LLM(2차)이 확정한다.
#   주의: '점검·계통 지시·출력제어'는 정당한 '대안 정황'이라 넣지 않는다(완곡히 언급돼야 함).
_CAUSE_MECHANISMS = (
    "고장", "결함", "파손", "베어링", "기어", "블레이드", "낙뢰",
    "과열", "누유", "마모", "균열", "단락", "부품",
)
# data_missing에서 언급 자체가 금지인 손실/발전량 키워드(1차 필터).
_LOSS_WORDS = ("손실", "발전량", "놓친")


def _causal_candidate(text: str) -> bool:
    """인과 단정 '후보' — 원인-메커니즘 명사가 하나라도 있으면 True(2차 LLM 확인 대상)."""
    return any(w in text for w in _CAUSE_MECHANISMS)


def _loss_candidate(text: str) -> bool:
    """data_missing 손실 언급 후보 — 손실/발전량 키워드가 있으면 True."""
    return any(w in text for w in _LOSS_WORDS)


def code_checks(state) -> list:
    """분석은 숫자를 담지 않는다 — 모든 수치는 표·차트가 소유(단일 출처).

    분석에 숫자가 하나라도 있으면 환각 위험이자 규칙 위반이므로 반려한다.
    exact-match/허용집합이 필요 없는 결정론 규칙이라 뚫릴 수 없다.
    """
    analysis = state.get("narrative", "") or ""
    nums = extract_numbers(analysis)
    if nums:
        return [
            f"분석에 숫자 사용({[round(n, 4) for n in nums]}) — 수치는 표·차트에만. "
            "숫자를 빼고 정성 표현으로 재작성하라."
        ]
    return []


class _CriticLLM(BaseModel):
    """판정 결과. 판정 기준의 '단일 출처'는 각 필드 description이다.

    with_structured_output이 이 description을 모델의 tool 스키마로 그대로 주입하므로,
    llm_check의 invoke 프롬프트에는 기준을 다시 적지 않고 데이터(분석 문단)만 전달한다.
    기준을 바꿀 땐 여기 description만 고치면 된다.
    """
    causal_assertion: bool = Field(
        description=(
            "판정 기준은 딱 하나: 분석이 '이상(정지/부재/저하)이 왜 일어났는지' 그 원인을 "
            "특정 원인(고장·부품 결함·점검·계통 지시·기상 등)으로 단정하면 true. 그 외는 전부 false.\n"
            "true 예(원인을 특정): '베어링 고장으로 정지했다', '기상 악화 때문에 멈췄다'.\n"
            "반드시 false (자주 헷갈리니 주의):\n"
            "- 결과 서술: '정지로 인해 손실/발전량 감소가 발생했다' "
            "— 여기서 정지는 '손실의 원인'일 뿐, 정지가 '왜' 났는지를 말한 게 아니다.\n"
            "- 정황·상관: '평균 풍속 N m/s 상황에서 발생', '시기적으로 일치'.\n"
            "- 완곡: '시사한다/가능성이 있다/확인이 필요하다'.\n"
            "- scope 정황: 전 호기 동시=수집·통신 장애 / 단독=설비 문제."
        )
    )
    causal_span: str = Field(
        default="",
        description="causal_assertion=true일 때, 근본 원인을 단정한 '문제 문구'를 분석 원문에서 "
        "그대로 한 구절만 인용(재작성 지시에 그대로 쓰인다). false면 빈 문자열.",
    )
    loss_in_data_missing: bool = Field(
        description="data_missing인데 손실/발전량의 '수치를 제시'하거나 '손실이 발생했다'고 단정하면 true. "
        "'관측 부재로 산출할 수 없다'처럼 부재·불가를 설명하는 것은 false."
    )


_critic_llm = llm.with_structured_output(_CriticLLM)


def llm_check(state) -> list:
    """표현 가드레일 — 결정론 필터(1차)가 걸린 것만 LLM(2차)이 정밀 판정.

    필터가 깨끗하면 LLM을 호출하지 않고 즉시 통과([]) → 오탐·재시도 낭비 제거.
    판정 기준은 _CriticLLM 필드 description이 단일 출처. 여기선 데이터만 넘긴다.
    """
    analysis = state.get("narrative", "") or ""
    et = state["tool_outputs"]["event"].get("event_type")

    # ── 1차: 결정론 키워드 필터 ────────────────────────────────────────────
    causal_candidate = _causal_candidate(analysis)
    loss_candidate = et == "data_missing" and _loss_candidate(analysis)
    if not causal_candidate and not loss_candidate:
        return []  # 후보 없음 → LLM 미호출, 통과

    # ── 2차: 걸린 것만 LLM 정밀 판정 ──────────────────────────────────────
    res = _critic_llm.invoke(
        f"다음 이상감지 보고서 '분석' 문단을 스키마 각 필드 기준으로 판정하라. "
        f"event_type = {et}.\n\n[분석]\n{analysis}"
    )
    issues = []
    # 필터가 잡은 항목만 LLM 판정을 반영한다(1차 필터 ↔ 2차 확인 일치).
    if causal_candidate and res.causal_assertion:
        # 문제 문구를 그대로 실어 재작성이 '무엇을' 고쳐야 하는지 알게 한다(루프 수렴).
        span = (getattr(res, "causal_span", "") or "").strip()
        if span:
            issues.append(
                f'인과 단정 의심: "{span}" → 근본 원인 단정을 피하고 '
                "'가능성/시사/정황'으로 완곡화하거나 해당 표현을 삭제하라."
            )
        else:
            issues.append(
                "인과 단정 표현 사용 → 근본 원인 단정을 피하고 "
                "'가능성/시사/정황'으로 완곡화하라."
            )
    if loss_candidate and res.loss_in_data_missing:
        issues.append("data_missing인데 손실/발전량을 언급함(관측 부재 원칙 위반)")
    return issues
