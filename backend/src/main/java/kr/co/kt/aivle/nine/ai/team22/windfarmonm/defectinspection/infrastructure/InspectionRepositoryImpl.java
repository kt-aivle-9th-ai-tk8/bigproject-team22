package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.Inspection;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.InspectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InspectionRepositoryImpl implements InspectionRepository {

    private final InspectionJpaRepository jpaRepository;

    @Override
    public Inspection save(Inspection inspection) {
        return jpaRepository.save(inspection);
    }

    @Override
    public Optional<Inspection> findById(Long id) {
        return jpaRepository.findById(id);
    }
}
