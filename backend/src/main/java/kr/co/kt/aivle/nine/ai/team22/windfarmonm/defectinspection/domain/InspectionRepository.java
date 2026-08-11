package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain;

import java.util.Optional;

/**
 * Inspection 저장소 포트.
 */
public interface InspectionRepository {

    Inspection save(Inspection inspection);

    Optional<Inspection> findById(Long id);

    /**
     * 쓰기 잠금(PESSIMISTIC_WRITE)으로 조회한다. 상태 전이를 동반하는 경로(업로드 완료 통보)가
     * 동시 요청과 직렬화되도록 쓴다 — 잠금 없이는 두 요청이 모두 UPLOADING 을 읽고 통과해
     * 아웃박스(추론 요청)가 중복 기록된다.
     */
    Optional<Inspection> findByIdForUpdate(Long id);
}
