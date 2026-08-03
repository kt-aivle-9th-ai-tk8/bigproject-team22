package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.ScadaRecord;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.ScadaRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ScadaRecordRepositoryImpl implements ScadaRecordRepository {

    private final ScadaRecordJpaRepository jpaRepository;

    @Override
    public Optional<ScadaRecord> findLatestByTurbineId(Long turbineId) {
        return jpaRepository.findTopByTurbineIdOrderByTimeDesc(turbineId);
    }

    @Override
    public List<ScadaRecord> findLatestByTurbineIds(List<Long> turbineIds) {
        if (turbineIds == null || turbineIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findLatestByTurbineIds(turbineIds);
    }

    @Override
    public List<ScadaRecord> findByTurbineIdAndTimeBetween(Long turbineId, LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findByTurbineIdAndTimeBetween(turbineId, start, end);
    }

    @Override
    public List<ScadaRecord> findByTurbineIdsAndTimeBetween(List<Long> turbineIds, LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findByTurbineIdInAndTimeBetween(turbineIds, start, end);
    }
}
