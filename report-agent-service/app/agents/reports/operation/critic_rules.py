"""operation 검증(critic) — 팀원 구현 예정.

REGISTRY["operation"]["critic"]에 넣을 콜러블. 계약은 '무엇을 검사할지'가 아니라 '반환 형태'뿐이고,
그 형태도 최소만 강제된다:
    critic(state) -> critic_result   # 최소: {verdict[, issues]}  (기본 정책이 verdict/issues만 읽음)

- 무엇을 검사할지(KPI 정합·비교·추세…), 결정론/LLM, critic_result에 어떤 필드를 담을지는 operation 자유.
  anomaly의 hard/soft 어휘는 anomaly 고유다 — 따를 필요 없다.
- 검증이 아직 없으면 registry에서 critic=None(스킵).
- 재시도 규칙이 기본과 다르면 registry에 retry_policy 콜러블 주입(커스텀 critic_result면 그걸 읽는 정책도 한 쌍).
- hard/soft 소진·강등 정책 예시가 필요하면 reports/anomaly/critic_rules(_hard_soft/retry_policy) 참고.
"""


def critic(state) -> dict:
    raise NotImplementedError("operation critic 구현 예정: critic_result 반환 (또는 registry에서 None)")
