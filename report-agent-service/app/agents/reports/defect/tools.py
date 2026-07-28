"""defect 데이터 조회 — 팀원 구현 예정. LLM 미사용, 원본 수치 그대로.

계약: fetch(event_id) -> dict   # state["tool_outputs"] (수치 + 차트 데이터의 단일 출처).
  - 결함 이벤트/이미지 판정 결과 등을 조회해 dict로 반환.
  - 로컬은 CSV, 배포는 app.core.config.DATA_SOURCE=="rds"로 분기(시그니처 유지).
"""


def fetch(event_id: int) -> dict:
    raise NotImplementedError(
        "defect fetch 구현 예정: 결함 이벤트/이미지 결과 조회 → tool_outputs dict"
    )
