package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.AnomalyEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.AnomalyEventRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.AnomalyEventType;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.AnomalyTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AnomalyEventRepositoryImpl implements AnomalyEventRepository {

    private final AnomalyEventJpaRepository jpaRepository;

    @Override
    public AnomalyEvent save(AnomalyEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public Optional<AnomalyEvent> findByIdentity(Long turbineId, AnomalyTier tier,
                                                 AnomalyEventType eventType, LocalDateTime startTime) {
        return jpaRepository.findByTurbineIdAndTierAndEventTypeAndStartTime(turbineId, tier, eventType, startTime);
    }

    @Override
    public List<AnomalyEvent> findOngoing() {
        return jpaRepository.findByEndTimeIsNull();
    }
}
