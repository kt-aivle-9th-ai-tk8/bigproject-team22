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

방어적 등록: 각 report_type의 import를 '엔트리 빌더' 함수 안으로 지연시키고 try/except로 감싼다.
한 타입의 모듈이 로드에 실패해도(예: 스키마 불일치로 tools.py의 top-level read_csv가 예외)
그 타입만 스킵되고 나머지는 정상 등록된다 — 한 폴더의 파손이 전체 서비스를 죽이지 않게(진짜 '폴더별 병렬 개발').
실패 타입은 경고 로그를 남기며, 담당자가 고치면 자동으로 다시 등록된다.
"""
import logging

logger = logging.getLogger(__name__)


def _anomaly_entry():
    from app.agents.reports.anomaly import anomaly_agent, tools, critic_rules
    return {
        "fetch": tools.fetch,
        "agent": anomaly_agent.anomaly_agent,
        "max_retries": 2,
        "critic": critic_rules.critic,              # hard=숫자 게이트 / soft=인과 레이어드
        "retry_policy": critic_rules.retry_policy,  # anomaly 전용 hard/soft 소진·강등 정책
    }


def _defect_entry():
    from app.agents.reports.defect import defect_agent, tools, critic_rules
    return {
        "fetch": tools.fetch,
        "agent": defect_agent.defect_agent,
        "max_retries": 2,
        "critic": critic_rules.critic,   # code_checks(결정론)+llm_check(LLM). retry_policy 없음 → 기본 정책
    }


def _operation_entry():   # 터빈 단위 운영 (event_id = 터빈번호)
    from app.agents.reports.operation import operation_agent, tools, critic_rules
    return {
        "fetch": tools.fetch,
        "agent": operation_agent.operation_agent,
        "max_retries": 2,
        "critic": critic_rules.critic,              # 총평 숫자 게이트(hard) + 인과/정비 가드(soft)
        "retry_policy": critic_rules.retry_policy,  # hard/soft 소진·강등 정책
    }


def _farm_operation_entry():   # 단지(발전소 전체) 운영 — 전 터빈 집계 + 터빈별 순위
    from app.agents.reports.farm_operation import farm_operation_agent, tools
    from app.agents.reports.farm_operation import critic_rules as farm_critic
    from app.agents.reports.operation import critic_rules as op_critic
    return {
        "fetch": tools.fetch,
        "agent": farm_operation_agent.farm_operation_agent,
        "max_retries": 2,
        "critic": farm_critic.critic,             # 단지 builder grounding
        "retry_policy": op_critic.retry_policy,   # 제네릭(report_type 기반) 재사용
    }


_ENTRY_BUILDERS = {
    "anomaly": _anomaly_entry,
    "defect": _defect_entry,
    "operation": _operation_entry,
    "farm_operation": _farm_operation_entry,
}

REGISTRY = {}
for _name, _build in _ENTRY_BUILDERS.items():
    try:
        REGISTRY[_name] = _build()
    except Exception as e:   # import/로드 실패(스키마 불일치 등) 타입은 스킵 — 나머지는 정상 등록
        logger.warning("report type '%s' 사용 불가 (import 실패): %s: %s",
                       _name, type(e).__name__, e)

if not REGISTRY:
    raise RuntimeError("등록된 report_type이 없습니다 — 모든 타입 import 실패")

REPORT_TYPES = tuple(REGISTRY.keys())
