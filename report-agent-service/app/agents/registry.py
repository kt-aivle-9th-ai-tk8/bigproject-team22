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
from app.agents.reports.operation import operation_agent as _operation_agent
from app.agents.reports.operation import tools as _operation_tools

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
        "critic": None,
    },
    "operation": {
        "fetch": _operation_tools.fetch,
        "agent": _operation_agent.operation_agent,
        "max_retries": 2,
        "critic": None,
    },
}
REPORT_TYPES = tuple(REGISTRY.keys())
