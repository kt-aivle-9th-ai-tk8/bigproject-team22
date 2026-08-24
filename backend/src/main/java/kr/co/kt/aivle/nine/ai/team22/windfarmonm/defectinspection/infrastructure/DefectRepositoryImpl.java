package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Defect;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.DefectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DefectRepositoryImpl implements DefectRepository {

    private final DefectJpaRepository jpaRepository;

    @Override
    public List<Defect> saveAll(List<Defect> defects) {
        return jpaRepository.saveAll(defects);
    }

    @Override
    public List<Defect> findByBladeId(Long bladeId) {
        return jpaRepository.findByBladeIdOrderByIdDesc(bladeId);
    }
}
