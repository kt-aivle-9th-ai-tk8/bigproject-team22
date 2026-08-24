package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.DailyGeneration;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineInstantId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DailyGenerationJpaRepository extends JpaRepository<DailyGeneration, TurbineInstantId> {

    Optional<DailyGeneration> findByTurbineIdAndTime(Long turbineId, LocalDateTime time);

    List<DailyGeneration> findByTurbineIdInAndTime(Collection<Long> turbineIds, LocalDateTime time);

    List<DailyGeneration> findByTurbineIdAndTimeBetween(Long turbineId, LocalDateTime start, LocalDateTime end);

    List<DailyGeneration> findByTurbineIdInAndTimeBetween(Collection<Long> turbineIds, LocalDateTime start, LocalDateTime end);
}
