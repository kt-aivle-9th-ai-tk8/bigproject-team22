"""defect(이미지 결함) 보고서 에이전트 — 팀원 구현 예정.

계약: defect_agent(state) -> {"draft": str, "narrative": str | None}
  - state["tool_outputs"](tools.fetch 결과)로 builder.render_report(to[, analysis])를 조립해 반환.
  - narrative(LLM 분석)는 선택 — 안 쓰면 None(그러면 critic이 통과). 쓰면 숫자 없는 정성 서술만.
  - anomaly_agent(reports/anomaly/anomaly_agent.py)를 참고 구현체로.
"""


def defect_agent(state) -> dict:
    raise NotImplementedError(
        "defect_agent 구현 예정: tool_outputs로 분석 생성 후 builder.render_report 조립"
    )
