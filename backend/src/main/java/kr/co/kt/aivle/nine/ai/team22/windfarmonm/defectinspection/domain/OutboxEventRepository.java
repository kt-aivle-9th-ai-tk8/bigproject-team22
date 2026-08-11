package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain;

import java.util.List;
import java.util.Optional;

/**
 * OutboxEvent 저장소 포트.
 */
public interface OutboxEventRepository {

    List<OutboxEvent> saveAll(List<OutboxEvent> events);

    Optional<OutboxEvent> findById(Long id);

    /** 릴레이가 집어갈 발행 대기 행을 발생 순서대로, 한 번에 {@code limit} 건까지. */
    List<OutboxEvent> findPendingBatch(int limit);

    /**
     * 해당 애그리거트에 아직 끝나지 않은(PENDING/PUBLISHED) 행이 남아 있는지.
     * 점검의 모든 이미지 결과가 도착했는지(= INSPECTED 전이 시점) 판정에 쓴다.
     */
    boolean existsUnfinishedByAggregate(String aggregateType, String aggregateId);
}
