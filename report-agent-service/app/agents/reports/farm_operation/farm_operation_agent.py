"""farm_operation_agent — facts(코드 집계 수치) 근거 '단지 운영 총평' 서술 + builder로 조립.

팀 컨벤션(anomaly/defect/operation과 동일): LLM은 facts의 수치만 '인용'(생성 금지),
critic이 grounding으로 검증. WITH_ANALYSIS off면 완전 결정론(narrative=None).
"""
from app.core.config import WITH_ANALYSIS
from app.agents.llm import llm
from app.agents.reports.farm_operation.builder import render_report, fact_lines

_SYSTEM = """당신은 풍력 '단지 운영 리포트'의 '단지 운영 총평'을 쓰는 운영 분석가다.
아래 [사실]만 근거로, 운영 담당자가 읽을 자연스러운 서술체 분석을 3~5문장으로 작성한다.

[해야 할 일]
- 단지 발전 실적(달성률)·가동률·손실 분해를 종합하고, 부진이 특정 터빈에 집중되는지 단지 전반인지
  관점을 준다(터빈 코드 언급 가능). 평균 풍속으로 바람(자원) 탓인지 설비 탓인지 맥락을 준다.

[반드시 지킬 규칙]
- 숫자는 [사실]에 있는 값만 '그대로' 인용한다. [사실]에 없는 숫자를 절대 만들지 마라
  (반올림·계산·추정으로 새 숫자를 만드는 것도 금지). 값이 없으면 정성 표현으로만.
  (단, 'U5' 같은 터빈 코드는 숫자가 아니므로 언급 가능.)
- 근본 원인을 단정하지 마라 — 가능성/정황/확인 필요로 안내한다.
- 구체적 정비·수리 조치를 지시하지 마라. 운영 관점 주의 환기에 그친다.
- 표·머리말 없이 문단 서술로."""


def farm_operation_agent(state) -> dict:
    to = state["tool_outputs"]
    if not WITH_ANALYSIS:
        return {"draft": render_report(to), "narrative": None}

    parts = [f"[사실]\n{chr(10).join(fact_lines(to))}\n\n위 사실의 수치만 인용해 단지 운영 총평을 작성하라."]
    feedback = state.get("retry_feedback")
    if feedback:
        parts.append("\n[재작성 지시] 아래 이유로 반려됨 — 반드시 반영: " + "; ".join(feedback))

    narrative = llm.invoke(
        [{"role": "system", "content": _SYSTEM}, {"role": "user", "content": "".join(parts)}]
    ).content.strip()
    return {"draft": render_report(to, narrative), "narrative": narrative}
