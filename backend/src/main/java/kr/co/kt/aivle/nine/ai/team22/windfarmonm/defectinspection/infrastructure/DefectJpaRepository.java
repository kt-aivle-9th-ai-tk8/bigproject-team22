package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Defect;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DefectJpaRepository extends JpaRepository<Defect, Long> {

    List<Defect> findByBladeIdOrderByIdDesc(Long bladeId);
}
