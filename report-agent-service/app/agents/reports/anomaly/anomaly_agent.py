"""anomaly_agent — 표·차트(코드 주입) + 선택적 '분석'(LLM, 숫자 없음).

WITH_ANALYSIS on: LLM이 여러 신호를 연결한 '정성 해석·트리아지'를 쓴다. 단 숫자는 한 개도
쓰지 않는다(모든 수치는 표·차트가 소유). critic이 '분석에 숫자 있으면 반려'로 강제한다.
WITH_ANALYSIS off: 분석 없이 완전 결정론(narrative=None → critic 통과).
"""
from app.core.config import WITH_ANALYSIS
from app.agents.llm import llm
from app.agents.reports.anomaly.builder import render_report, fact_lines, EVENT_TYPE_KO

_ANALYST_RULES = """당신은 풍력 이상감지 리포트의 '분석'을 쓰는 분석가다.
아래 [관측 요약]을 근거로 여러 신호를 연결해 '무엇을 시사하는지'와 '확인 우선순위'를 2~3문장으로 쓴다.

[반드시 지킬 것]
- 숫자를 절대 쓰지 마라. 모든 수치는 이미 리포트의 표·차트에 있다.
  크기·추세는 '크게 밑돎 / 전 구간 / 급락 / 일부 구간 / 약 절반' 같은 정성 표현으로만 한다.
  (유형명의 시간값 '24시간·6시간'도 다시 적지 않는다.)
- 근본 원인을 단정하지 마라: '고장 때문에 정지' (X) → '가능성/시사/정황' (O).
  정지를 '고장'으로 단정 금지(출력제어·점검·계통 지시 등 다른 원인 가능).
- data_missing이면 관측 자체가 부재하므로 손실·발전량을 언급하지 마라.
"""


def anomaly_agent(state) -> dict:
    to = state["tool_outputs"]
    if not WITH_ANALYSIS:
        return {"draft": render_report(to), "narrative": None}

    et = to["event"].get("event_type")
    parts = [
        f"event_type = {et} ({EVENT_TYPE_KO.get(et, et)})",
        "",
        "[관측 요약] (참고용 — 분석엔 숫자를 쓰지 말 것)",
        "\n".join(fact_lines(to)),
        "",
        "위를 근거로 '분석'을 숫자 없이 2~3문장으로 작성하라.",
    ]
    feedback = state.get("retry_feedback")
    if feedback:
        parts += ["", "[재작성 지시] 아래 이유로 반려됨. 반드시 반영:", *[f"- {x}" for x in feedback]]

    analysis = llm.invoke(
        [
            {"role": "system", "content": _ANALYST_RULES},
            {"role": "user", "content": "\n".join(parts)},
        ]
    ).content.strip()
    return {"draft": render_report(to, analysis), "narrative": analysis}
