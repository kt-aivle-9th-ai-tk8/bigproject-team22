"""operation_agent — 표·차트(코드 주입) + 선택적 '운영 총평'(LLM, 숫자 없음).

WITH_ANALYSIS on: LLM이 발전 실적·가동률·손실 분해를 연결해 '운영 진단 총평'을 쓴다.
  단 숫자는 한 개도 쓰지 않는다(모든 수치는 표·차트가 소유). critic이 강제한다.
WITH_ANALYSIS off: 총평 없이 완전 결정론(narrative=None → critic 통과).
anomaly_agent와 동일 패턴.
"""
from app.core.config import WITH_ANALYSIS
from app.agents.llm import llm
from app.agents.reports.operation.builder import render_report, fact_lines

_ANALYST_RULES = """당신은 풍력 '터빈 운영 리포트'의 '운영 총평'을 쓰는 운영 분석가다.
아래 [관측 요약]을 근거로 이 터빈의 운영 상태를 '진단'해 2~4문장으로 쓴다.

[해야 할 일]
- 발전 실적(달성률)과 가동률을 함께 보고, 손실이 '정지(가동률)' 때문인지 '가동 중 성능저하'
  때문인지 판단해 우선 조치 방향을 제시한다.
- 평균 풍속을 근거로 저조 발전이 바람(자원) 탓인지 설비 문제인지 맥락을 준다.

[반드시 지킬 것]
- 숫자를 절대 쓰지 마라. 모든 수치는 이미 표·차트에 있다.
  크기·추세는 '크게 밑돎 / 대부분 / 낮음 / 적정 수준' 같은 정성 표현으로만.
- 근본 원인을 단정하지 마라: '베어링 고장으로 정지' (X) → '가능성/시사/확인 필요' (O).
- 구체적 정비·수리 조치를 지시하지 마라(그건 결함진단 리포트의 역할). 운영 관점 주의 환기에 그친다.
"""


def operation_agent(state) -> dict:
    to = state["tool_outputs"]
    if not WITH_ANALYSIS:
        return {"draft": render_report(to), "narrative": None}

    parts = [
        "[관측 요약] (참고용 — 총평엔 숫자를 쓰지 말 것)",
        "\n".join(fact_lines(to)),
        "",
        "위를 근거로 '운영 총평'을 숫자 없이 2~4문장으로 작성하라.",
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
