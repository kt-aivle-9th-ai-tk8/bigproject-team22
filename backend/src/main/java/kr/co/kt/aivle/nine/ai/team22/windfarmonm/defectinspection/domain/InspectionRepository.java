package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain;

import java.util.Optional;

/**
 * Inspection 저장소 포트.
 */
public interface InspectionRepository {

    Inspection save(Inspection inspection);

    Optional<Inspection> findById(Long id);
}
