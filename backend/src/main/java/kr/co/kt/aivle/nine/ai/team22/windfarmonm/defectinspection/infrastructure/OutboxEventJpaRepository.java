package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.OutboxStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Limit limit);

    boolean existsByAggregateTypeAndAggregateIdAndStatusIn(
            String aggregateType, String aggregateId, Collection<OutboxStatus> statuses);

    long countByAggregateTypeAndAggregateIdAndStatus(
            String aggregateType, String aggregateId, OutboxStatus status);
}
