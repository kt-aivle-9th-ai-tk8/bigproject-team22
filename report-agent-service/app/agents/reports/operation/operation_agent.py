"""operation_agent — facts(코드 집계 수치) 근거 '종합분석' 서술 + builder로 전체 조립.

계약: operation_agent(state) -> {"draft", "narrative"}

팀 컨벤션(anomaly/defect와 동일): 수치·표·차트는 builder가 코드로 주입(단일 출처).
LLM은 '## 운영 총평'에서 facts의 수치만 '인용'해 서술한다(생성 금지). critic이 grounding으로 검증.
WITH_ANALYSIS off면 총평 없이 완전 결정론(narrative=None → critic 통과).
"""
from app.core.config import WITH_ANALYSIS
from app.agents.llm import llm
from app.agents.reports.operation.builder import render_report, fact_lines

_SYSTEM = """당신은 풍력 '터빈 운영 리포트'의 '운영 총평'을 쓰는 운영 분석가다.
아래 [사실]만 근거로, 운영 담당자가 읽을 자연스러운 서술체 분석을 3~5문장으로 작성한다.

[해야 할 일]
- 발전 실적(달성률)·가동률·손실 분해(정지 vs 성능저하)를 연결해 운영 상태를 진단하고
  우선 조치 방향을 제시한다. 평균 풍속으로 바람(자원) 탓인지 설비 문제인지 맥락을 준다.

[반드시 지킬 규칙]
- 숫자는 [사실]에 있는 값만 '그대로' 인용한다. [사실]에 없는 숫자를 절대 만들지 마라
  (반올림·계산·추정으로 새 숫자를 만드는 것도 금지). 값이 없으면 정성 표현으로만.
- 대상 기간·터빈 코드는 다시 쓰지 않는다(개요에 이미 있음).
- 근본 원인을 단정하지 마라(고장/원인 확정 금지) — 가능성/정황/확인 필요로 안내한다.
- 구체적 정비·수리·부품 교체 조치를 지시하지 마라(결함진단 리포트의 역할). 운영 관점 주의 환기에 그친다.
- 표·머리말 없이 문단 서술로."""


def operation_agent(state) -> dict:
    to = state["tool_outputs"]
    if not WITH_ANALYSIS:
        return {"draft": render_report(to), "narrative": None}

    parts = [f"[사실]\n{chr(10).join(fact_lines(to))}\n\n위 사실의 수치만 인용해 운영 총평을 작성하라."]
    feedback = state.get("retry_feedback")
    if feedback:
        parts.append("\n[재작성 지시] 아래 이유로 반려됨 — 반드시 반영: " + "; ".join(feedback))

    narrative = llm.invoke(
        [{"role": "system", "content": _SYSTEM}, {"role": "user", "content": "".join(parts)}]
    ).content.strip()
    return {"draft": render_report(to, narrative), "narrative": narrative}
