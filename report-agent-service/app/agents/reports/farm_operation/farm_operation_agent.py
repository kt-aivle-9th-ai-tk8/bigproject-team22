"""farm_operation_agent — 표·차트(코드 주입) + 선택적 '단지 운영 총평'(LLM, 숫자 없음).

operation_agent와 동일 패턴. WITH_ANALYSIS on이면 LLM이 단지 전체 관점의 진단 총평을 쓰되
숫자는 한 개도 쓰지 않는다(모든 수치는 표·차트·순위표가 소유). critic이 강제.
"""
from app.core.config import WITH_ANALYSIS
from app.agents.llm import llm
from app.agents.reports.farm_operation.builder import render_report, fact_lines

_ANALYST_RULES = """당신은 풍력 '단지 운영 리포트'의 '단지 운영 총평'을 쓰는 운영 분석가다.
아래 [관측 요약]을 근거로 단지 전체의 운영 상태를 '진단'해 2~4문장으로 쓴다.

[해야 할 일]
- 단지 발전 실적(달성률)·가동률을 종합하고, 손실이 '정지(가동률)'인지 '가동 중 성능저하'인지 판단해
  단지 차원의 우선 조치 방향을 제시한다.
- 특정 터빈(실적 최저 터빈)에 부진이 집중되는지, 단지 전반의 문제인지 관점을 준다.
- 평균 풍속을 근거로 저조 발전이 바람(자원) 탓인지 설비 탓인지 맥락을 준다.

[반드시 지킬 것]
- 숫자를 절대 쓰지 마라. 모든 수치는 표·차트·순위표에 있다. 크기·추세는 정성 표현으로만.
  (단, 터빈 코드 'U5' 같은 식별자는 숫자가 아니므로 언급 가능.)
- 근본 원인을 단정하지 마라(고장 단정 X → 가능성/확인 필요 O).
- 구체적 정비·수리 조치를 지시하지 마라. 운영 관점 주의 환기에 그친다.
"""


def farm_operation_agent(state) -> dict:
    to = state["tool_outputs"]
    if not WITH_ANALYSIS:
        return {"draft": render_report(to), "narrative": None}

    parts = [
        "[관측 요약] (참고용 — 총평엔 숫자를 쓰지 말 것)",
        "\n".join(fact_lines(to)),
        "",
        "위를 근거로 '단지 운영 총평'을 숫자 없이 2~4문장으로 작성하라.",
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
