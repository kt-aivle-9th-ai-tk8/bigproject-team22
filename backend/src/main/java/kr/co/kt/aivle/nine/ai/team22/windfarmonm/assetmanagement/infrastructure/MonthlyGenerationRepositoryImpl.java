package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.MonthlyGeneration;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.MonthlyGenerationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MonthlyGenerationRepositoryImpl implements MonthlyGenerationRepository {

    private final MonthlyGenerationJpaRepository jpaRepository;

    @Override
    public Optional<MonthlyGeneration> findByTurbineIdAndTime(Long turbineId, LocalDateTime time) {
        return jpaRepository.findByTurbineIdAndTime(turbineId, time);
    }

    @Override
    public List<MonthlyGeneration> findByTurbineIdsAndTime(List<Long> turbineIds, LocalDateTime time) {
        return jpaRepository.findByTurbineIdInAndTime(turbineIds, time);
    }

    @Override
    public List<MonthlyGeneration> findByTurbineIdAndTimeBetween(Long turbineId, LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findByTurbineIdAndTimeBetween(turbineId, start, end);
    }

    @Override
    public List<MonthlyGeneration> findByTurbineIdsAndTimeBetween(List<Long> turbineIds, LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findByTurbineIdInAndTimeBetween(turbineIds, start, end);
    }
}
