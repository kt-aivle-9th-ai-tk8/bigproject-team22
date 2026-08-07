package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 이상 이벤트 저장소 포트.
 */
public interface AnomalyEventRepository {

    AnomalyEvent save(AnomalyEvent event);

    /**
     * 멱등키로 기존 이벤트를 찾는다.
     * <p>
     * 매시각 배치는 진행 중인 이벤트를 같은 키로 다시 산출한다(종료 시각과 지표만 달라진다).
     * 새로 넣기 전에 이 조회로 확인해야 회차마다 행이 쌓이지 않는다.
     */
    Optional<AnomalyEvent> findByIdentity(Long turbineId, AnomalyTier tier,
                                          AnomalyEventType eventType, LocalDateTime startTime);

    /** 아직 끝나지 않은 이벤트. 지속 시간 기반 게이트(예: 정지 24시간) 판정에 쓴다. */
    List<AnomalyEvent> findOngoing();
}
