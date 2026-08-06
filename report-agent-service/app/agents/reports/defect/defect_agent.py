"""defect_agent — facts(코드 집계치)를 근거로 '종합 분석'을 쓰고 builder로 전체 조립.

계약: defect_agent(state) -> {"draft": str, "narrative": str}

역할(분석가 레이어): 수치·터빈별 상세·이미지 목록·판단유보는 builder가 코드로 주입.
LLM은 '## 종합 분석'에서 facts를 근거로 신호를 종합·해석·우선순위 제시. 수치는 '인용'만.
WITH_ANALYSIS off면 종합 분석 없이 완전 결정론(narrative=None → LLM 미호출). operation과 동일.
"""
import re

from app.core.config import WITH_ANALYSIS
from app.agents.llm import llm
from app.agents.reports.defect.builder import fact_lines, render_report

# LLM이 본문 앞에 '종합 분석:' 같은 제목을 덧붙이는 경우가 있어 제거한다(섹션 헤더가 이미 있음).
_LEADING_TITLE = re.compile(r"^#{0,4}\s*\**\s*종합\s*분석\s*\**\s*[:：]?\s*\n+")

# ── 분석가(analyst) 규칙 ───────────────────────────────────────────────────
_ANALYST_RULES = """당신은 풍력 블레이드 결함 진단 보고서의 '종합 분석' 섹션을 쓰는 분석가다.
아래 [facts]의 '코드가 집계한 수치'를 근거로 3~5문장의 해석을 쓴다.

[해야 할 일 — LLM의 값어치가 여기서 나온다]
- 여러 신호(심각도 분포·결함 유형·터빈별/블레이드별/위치별 편중·검출 신뢰도)를 연결해
  무엇을 시사하는지 종합한다.
- 어느 터빈·어느 부위를 먼저 확인하는 게 좋을지 '확인 우선순위'를 정황 근거와 함께 제시한다.

[반드시 지킬 것]
- facts에 있는 수치만 인용한다. 새로운 숫자를 계산하거나 지어내지 않는다.
  (크기 비교는 '약 절반', '다른 터빈보다 두드러짐' 같은 정성 표현으로 한다.)
- 날짜·시각·점검 번호는 다시 쓰지 않는다(개요에 이미 있음).
- AI 판독 결과를 확정된 손상으로 단정하지 않는다.
  "균열이 발생했다"(X) → "…로 검출되었다", "…로 분류되었다", "가능성이 있다"(O)
- 조치를 단정하거나 지시하지 않는다.
  "즉시 교체해야 한다"(X) → "우선 확인 대상으로 고려할 수 있다"(O)
  수리·교체·운전정지 같은 조치 결정은 담당자 몫이다.
- 결함의 '원인'을 단정하지 않는다(낙뢰·시공불량 등). 정황·상관 서술은 가능.
"""


def defect_agent(state) -> dict:
    """facts(코드 집계치)를 근거로 LLM이 '종합 분석'을 쓰고, builder로 전체 조립."""
    to = state["tool_outputs"]
    if not WITH_ANALYSIS:
        return {"draft": render_report(to, None), "narrative": None}

    summary = to.get("summary", {}) or {}
    codes = summary.get("turbine_codes") or []

    facts_block = "\n".join(fact_lines(to))
    user_parts = [
        f"대상 터빈 {summary.get('n_turbines', 0)}대({', '.join(codes) or '없음'}), "
        f"총 검출 결함 {summary.get('n_defects_total', 0)}건",
        "",
        "[facts] (코드가 집계한 수치 — 이 값만 인용 가능)",
        facts_block,
        "",
        "위 facts를 근거로 '종합 분석'을 작성하라.",
    ]

    feedback = state.get("retry_feedback")
    if feedback:
        user_parts += [
            "",
            "[재작성 지시] 이전 분석이 아래 이유로 반려되었다. 반드시 반영하라:",
            *[f"- {x}" for x in feedback],
        ]

    resp = llm.invoke(
        [
            {"role": "system", "content": _ANALYST_RULES},
            {"role": "user", "content": "\n".join(user_parts)},
        ]
    )
    analysis = _LEADING_TITLE.sub("", resp.content.strip()).strip()
    return {"draft": render_report(to, analysis), "narrative": analysis}
