"""defect 검증 규칙 — 공유 critic_agent가 호출하는 report_type별 규칙.

계약:
  code_checks(state) -> list[str]   # 결정론적: 환각 수치 / 없는 터빈·결함유형 / 심각도 초과
  llm_check(state)   -> list[str]   # LLM: 조치 단정 / AI 검출의 확정 단정 / 원인 단정
issue 목록이 비어있으면 '적합', 하나라도 있으면 '부적합'.
"""
import re

from pydantic import BaseModel, Field

from app.core.config import WITH_ANALYSIS
from app.agents.llm import llm
from app.agents.verify import extract_numbers
from app.agents.reports.defect.builder import DEFECT_TYPE_KO, allowed_numbers

# '심각도 4' / '심각(4)' 형태의 심각도 언급을 잡는다.
_SEV_PATTERNS = (
    re.compile(r"심각도\s*(\d)"),
    re.compile(r"(?:경미|주의|중대|심각)\s*\((\d)\)"),
)


def _mentioned_severities(text: str) -> set:
    return {int(m) for pat in _SEV_PATTERNS for m in pat.findall(text)}


def code_checks(state) -> list:
    """결정론적 검증 — LLM 없이 facts와 대조. 셋 다 '환각' 차단이 목적."""
    analysis = state.get("narrative", "") or ""
    to = state["tool_outputs"]
    summary = to.get("summary", {}) or {}
    issues = []

    # ① 인용 수치가 코드 facts에 실재하는지 (anomaly와 동일한 허용오차 기준)
    allowed = allowed_numbers(to)
    bad = [
        round(n, 4)
        for n in extract_numbers(analysis)
        if not any(abs(abs(n) - x) <= max(x * 0.01, 0.05) for x in allowed)
    ]
    if bad:
        issues.append(f"facts에 없는 수치를 인용함(환각 후보): {bad}")

    # ② 점검에 없는 터빈을 언급했는지 (예: facts는 U1·U2인데 U5를 씀)
    known = set(summary.get("turbine_codes") or [])
    unknown = sorted(set(re.findall(r"\bU\d+", analysis)) - known)
    if unknown:
        issues.append(f"이번 점검에 없는 터빈을 언급함: {unknown}")

    # ③ 검출되지 않은 결함 유형을 언급했는지 (한글 유형명 기준)
    detected_ko = {
        DEFECT_TYPE_KO.get(k, k)
        for t in (to.get("turbines") or [])
        for k in (t.get("type_counts") or {})
    }
    absent = sorted(
        ko for ko in DEFECT_TYPE_KO.values() if ko in analysis and ko not in detected_ko
    )
    if absent:
        issues.append(f"검출되지 않은 결함 유형을 언급함: {absent}")

    # ④ 실제 최고 심각도를 넘는 등급을 언급했는지
    max_sev = summary.get("max_severity")
    if max_sev is not None:
        over = sorted(s for s in _mentioned_severities(analysis) if s > max_sev)
        if over:
            issues.append(f"실제 최고 심각도({max_sev})를 넘는 등급을 언급함: {over}")

    return issues


class _CriticLLM(BaseModel):
    action_assertion: bool = Field(
        description="수리·교체·운전정지 같은 '조치'를 단정하거나 지시하면 true "
        "(예: '즉시 교체해야 한다', '운전을 중단하라'). "
        "'우선 확인 대상으로 고려할 수 있다', '점검 시 함께 살펴볼 만하다'처럼 "
        "확인·검토를 권하는 완곡 표현은 false."
    )
    detection_as_confirmed: bool = Field(
        description="AI 판독 결과를 확정된 손상으로 단정하면 true "
        "(예: '균열이 발생했다', '블레이드가 손상되었다'). "
        "'검출되었다', '분류되었다', '가능성이 있다', '의심된다'처럼 "
        "판독 결과임을 드러내는 서술은 false."
    )
    causal_assertion: bool = Field(
        description="결함의 '원인'을 단정하면 true (예: '낙뢰로 인해 손상됐다', '시공 불량 때문이다'). "
        "편중·상관·정황 서술('앞전에 몰려 있다', '시기적으로 일치한다')은 false."
    )


_critic_llm = llm.with_structured_output(_CriticLLM)


def llm_check(state) -> list:
    """표현 가드레일 — 조치 단정 / AI 검출의 확정 단정 / 원인 단정."""
    analysis = state.get("narrative", "") or ""
    if not analysis.strip():
        # WITH_ANALYSIS off 면 분석이 없는 게 정상이다 — 검증할 문장이 없으니 통과.
        # 켜져 있는데 비었다면 그건 LLM 실패이므로 그대로 반려한다(operation/anomaly 는
        # 키워드 필터가 빈 문자열에 걸리지 않아 자연히 통과한다).
        return [] if not WITH_ANALYSIS else ["분석 내용이 비어 있음"]

    res = _critic_llm.invoke(
        "다음은 풍력 블레이드 결함 진단 보고서의 '종합 분석' 문단이다.\n"
        "결함은 AI 영상 판독 모델이 검출·분류한 결과이며 확정된 손상 판정이 아니다.\n\n"
        f"[분석]\n{analysis}\n\n"
        "판정:\n"
        "- action_assertion: 수리·교체·운전정지 같은 조치를 단정·지시하면 true. "
        "'우선 확인 대상으로 고려할 수 있다'처럼 확인·검토를 권하는 완곡 표현은 false.\n"
        "- detection_as_confirmed: AI 판독 결과를 확정된 손상으로 단정하면 true "
        "(예: '균열이 발생했다'). '검출되었다/분류되었다/가능성이 있다'는 false.\n"
        "- causal_assertion: 결함의 원인을 단정하면 true (예: '낙뢰로 인해'). "
        "편중·상관·정황 서술은 false."
    )

    issues = []
    if res.action_assertion:
        issues.append("조치를 단정하는 표현 사용(조치 결정은 담당자 몫)")
    if res.detection_as_confirmed:
        issues.append("AI 검출 결과를 확정된 손상으로 단정함")
    if res.causal_assertion:
        issues.append("결함 원인을 단정하는 표현 사용")
    return issues


def critic(state) -> dict:
    """REGISTRY["defect"]["critic"] 진입점 — code_checks(결정론)+llm_check(LLM) 이슈를 합쳐 판정.

    본체 critic 계약({verdict, issues})으로 포장만 한다. 검증 규칙(code_checks/llm_check)은 무수정.
    """
    issues = code_checks(state) + llm_check(state)
    return {"verdict": "부적합" if issues else "적합", "issues": issues}
