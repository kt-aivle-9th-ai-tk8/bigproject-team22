package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.DailyGeneration;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.DailyGenerationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DailyGenerationRepositoryImpl implements DailyGenerationRepository {

    private final DailyGenerationJpaRepository jpaRepository;

    @Override
    public Optional<DailyGeneration> findByTurbineIdAndTime(Long turbineId, LocalDateTime time) {
        return jpaRepository.findByTurbineIdAndTime(turbineId, time);
    }

    @Override
    public List<DailyGeneration> findByTurbineIdsAndTime(List<Long> turbineIds, LocalDateTime time) {
        return jpaRepository.findByTurbineIdInAndTime(turbineIds, time);
    }

    @Override
    public List<DailyGeneration> findByTurbineIdAndTimeBetween(Long turbineId, LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findByTurbineIdAndTimeBetween(turbineId, start, end);
    }

    @Override
    public List<DailyGeneration> findByTurbineIdsAndTimeBetween(List<Long> turbineIds, LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findByTurbineIdInAndTimeBetween(turbineIds, start, end);
    }
}
