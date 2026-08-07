"""report_type → 모듈 dispatch table (REGISTRY) 의 단일 정의처 = 팀 공유 '계약'.

graph(조립)·critic(dispatch)·supervisor(정책)·service(시드)가 모두 이 표만 참조한다.
report_type 하나 = 아래 키를 채우는 것. 공유 골격은 무수정 → 3명이 폴더별 병렬 개발 가능.

계약(REGISTRY[type]):
  fetch:        callable(event_id) -> tool_outputs          # 필수
  agent:        callable(state) -> {"draft", "narrative"}   # 필수
  max_retries:  int                                         # 필수 — 각 타입이 자기 재시도 상한 소유
  critic:       callable(state) -> critic_result | None     # None = 검증 스킵
  retry_policy: callable(state) -> 라우팅dict | None        # None = 유형 중립 기본 정책(verdict 기반)

critic_result 최소 계약: {verdict[, issues]} — 기본 정책이 verdict("적합"이면 통과)/issues를 읽는다.
그 외 필드(유형별 hard/soft/warnings 등)는 각 타입의 critic·retry_policy가 자유롭게 정한다
(예: reports/anomaly/critic_rules 의 _hard_soft/retry_policy = hard/soft 소진 정책).

이 모듈은 app.agents.reports.* 구현만 import하고 graph/critic/supervisor는 import하지 않으므로 순환이 없다.
"""
from app.agents.reports.anomaly import anomaly_agent as _anomaly_agent
from app.agents.reports.anomaly import tools as _anomaly_tools
from app.agents.reports.anomaly import critic_rules as _anomaly_critic
from app.agents.reports.defect import defect_agent as _defect_agent
from app.agents.reports.defect import tools as _defect_tools
from app.agents.reports.defect import critic_rules as _defect_critic
from app.agents.reports.operation import operation_agent as _operation_agent
from app.agents.reports.operation import tools as _operation_tools
from app.agents.reports.operation import critic_rules as _operation_critic
from app.agents.reports.farm_operation import farm_operation_agent as _farm_agent
from app.agents.reports.farm_operation import tools as _farm_tools
from app.agents.reports.farm_operation import critic_rules as _farm_critic

# fetch(event_id) 의 event_id 가 report_type 마다 무엇을 가리키는지.
# 공유 골격이 4개 타입에 같은 시그니처를 쓰므로 이름은 event_id 하나지만 의미는 타입마다 다르다.
# 이 표가 유일한 설명처다 — CLI(run.py) 도움말과 API 스키마 설명이 여기를 인용한다.
# 틀린 id 를 넣어도 크래시가 아니라 found=False 로 조용히 끝나므로, 명시가 곧 방어책이다.
EVENT_ID_MEANING = {
    "anomaly": "anomaly_events.event_id (이상 감지 이벤트)",
    "defect": "report.report_id (결함 진단 보고서, 예: 5001~5060) — inspection_id 가 아니다",
    "operation": "터빈 번호 (2 → U2)",
    "farm_operation": "단지 id (현재 단일 단지라 값은 무시된다)",
}

REGISTRY = {
    "anomaly": {
        "fetch": _anomaly_tools.fetch,
        "agent": _anomaly_agent.anomaly_agent,
        "max_retries": 2,
        "critic": _anomaly_critic.critic,              # hard=숫자 게이트 / soft=인과 레이어드
        "retry_policy": _anomaly_critic.retry_policy,  # anomaly 전용 hard/soft 소진·강등 정책
    },
    "defect": {
        "fetch": _defect_tools.fetch,
        "agent": _defect_agent.defect_agent,
        "max_retries": 2,
        "critic": _defect_critic.critic,   # code_checks(결정론)+llm_check(LLM). retry_policy 없음 → 기본 정책
    },
    "operation": {   # 터빈 단위 운영 (event_id = 터빈번호)
        "fetch": _operation_tools.fetch,
        "agent": _operation_agent.operation_agent,
        "max_retries": 2,
        "critic": _operation_critic.critic,              # 총평 숫자 게이트(hard) + 인과/정비 가드(soft)
        "retry_policy": _operation_critic.retry_policy,  # hard/soft 소진·강등 정책
    },
    "farm_operation": {   # 단지(발전소 전체) 운영 — 전 터빈 집계 + 터빈별 순위
        "fetch": _farm_tools.fetch,
        "agent": _farm_agent.farm_operation_agent,
        "max_retries": 2,
        "critic": _farm_critic.critic,                   # 단지 builder grounding
        "retry_policy": _operation_critic.retry_policy,  # 제네릭(report_type 기반) 재사용
    },
}
REPORT_TYPES = tuple(REGISTRY.keys())
