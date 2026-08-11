package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEventRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    @Override
    public List<OutboxEvent> saveAll(List<OutboxEvent> events) {
        return jpaRepository.saveAll(events);
    }

    @Override
    public Optional<OutboxEvent> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<OutboxEvent> findPendingBatch(int limit) {
        // idx_outbox_event_status_created 가 '미발행 건을 발생 순서대로' 커버한다(V6).
        return jpaRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, Limit.of(limit));
    }

    @Override
    public boolean existsUnfinishedByAggregate(String aggregateType, String aggregateId) {
        return jpaRepository.existsByAggregateTypeAndAggregateIdAndStatusIn(
                aggregateType, aggregateId, List.of(OutboxStatus.PENDING, OutboxStatus.PUBLISHED));
    }
}
