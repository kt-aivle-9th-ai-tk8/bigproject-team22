package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain;

import java.util.List;

/**
 * OutboxEvent 저장소 포트. 발행(PENDING 폴링) 측 조회는 릴레이 구현 시 추가한다.
 */
public interface OutboxEventRepository {

    List<OutboxEvent> saveAll(List<OutboxEvent> events);
}
