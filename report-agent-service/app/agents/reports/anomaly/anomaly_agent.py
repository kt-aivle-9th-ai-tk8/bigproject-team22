"""anomaly_agent — facts(코드 집계 수치) 근거 '종합분석' 서술 + builder로 전체 조립.

계약: anomaly_agent(state) -> {"draft", "narrative"}

역할(분석가 레이어): 수치·표·차트·최근 이력·판단유보는 builder가 코드로 주입(사실의 단일 출처).
LLM은 '## 종합분석'에서 facts의 수치만 '인용'해 서술한다(생성 금지). critic이 grounding으로 검증.
"""
from app.agents.llm import llm
from app.agents.reports.anomaly import builder

_SYSTEM = """당신은 풍력 발전 이상감지 '종합분석'을 쓰는 분석가다. 아래 [사실]만 근거로,
운영 담당자가 읽을 자연스러운 서술체 분석을 4~6문장으로 작성한다.

[반드시 지킬 규칙]
- 숫자는 [사실]에 있는 값만 '그대로' 인용한다. [사실]에 없는 숫자를 절대 만들지 마라
  (반올림·계산·추정으로 새 숫자를 만드는 것도 금지). 값이 없으면 정성 표현으로만.
- 근본 원인을 단정하지 마라(고장/원인 확정 금지). 확인 절차로 안내한다.
- 표·머리말 없이 문단 서술로. 담당자에게 상황과 다음 행동을 전달하는 톤."""


def anomaly_agent(state) -> dict:
    to = state["tool_outputs"]
    facts_text = "\n".join(builder.fact_lines(to))
    parts = [f"[사실]\n{facts_text}\n\n위 사실만으로 종합분석을 작성하라."]

    style = builder.style_hint(to)
    if style:
        parts.append(f"\n[이 유형 서술 지침] {style}")

    feedback = state.get("retry_feedback")
    if feedback:
        parts.append("\n[재작성 지시] 아래 이유로 반려됨 — 반드시 반영: " + "; ".join(feedback))

    narrative = llm.invoke(
        [{"role": "system", "content": _SYSTEM}, {"role": "user", "content": "".join(parts)}]
    ).content.strip()
    return {"draft": builder.render_report(to, narrative), "narrative": narrative}
