package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Inspection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionJpaRepository extends JpaRepository<Inspection, Long> {
}
