"""defect 결정론 렌더러 — 팀원 구현 예정 (LLM 없음, 수치는 코드가 주입).

계약(agent가 호출):
  render_report(to, analysis=None) -> str   # tool_outputs로 .md 조립(표·차트·권고·판단유보).
      analysis(선택)가 있으면 삽입. 수치는 전부 코드가 표·차트에 주입한다.

- fact_lines/allowed_numbers 같은 함수는 anomaly가 자기 critic을 위해 둔 '내부 선택'일 뿐,
  계약이 아니다. defect는 필요 없으면 안 만들어도 된다.
- reports/anomaly/builder.py를 참고 구현체로.
"""


def render_report(to, analysis: str = None) -> str:
    raise NotImplementedError("defect render_report 구현 예정")
