package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.AnomalyEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.AnomalyEventType;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.AnomalyTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnomalyEventJpaRepository extends JpaRepository<AnomalyEvent, Long> {

    /** 멱등키(turbine_id, tier, event_type, start_time) 조회. DB 의 UNIQUE 제약과 같은 조합이다. */
    Optional<AnomalyEvent> findByTurbineIdAndTierAndEventTypeAndStartTime(
            Long turbineId, AnomalyTier tier, AnomalyEventType eventType, LocalDateTime startTime);

    List<AnomalyEvent> findByEndTimeIsNull();
}
