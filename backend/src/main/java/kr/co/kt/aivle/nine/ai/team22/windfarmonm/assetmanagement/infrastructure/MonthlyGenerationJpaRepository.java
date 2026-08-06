package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.MonthlyGeneration;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineInstantId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MonthlyGenerationJpaRepository extends JpaRepository<MonthlyGeneration, TurbineInstantId> {

    Optional<MonthlyGeneration> findByTurbineIdAndTime(Long turbineId, LocalDateTime time);

    List<MonthlyGeneration> findByTurbineIdInAndTime(Collection<Long> turbineIds, LocalDateTime time);

    List<MonthlyGeneration> findByTurbineIdAndTimeBetween(Long turbineId, LocalDateTime start, LocalDateTime end);

    List<MonthlyGeneration> findByTurbineIdInAndTimeBetween(Collection<Long> turbineIds, LocalDateTime start, LocalDateTime end);
}
