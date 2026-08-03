package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.ScadaRecord;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineInstantId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 발전량 원천(scada_record) 조회. {@code ScadaRecordRepositoryImpl} 어댑터에서만 사용한다.
 */
public interface ScadaRecordJpaRepository extends JpaRepository<ScadaRecord, TurbineInstantId> {

    Optional<ScadaRecord> findTopByTurbineIdOrderByTimeDesc(Long turbineId);

    List<ScadaRecord> findByTurbineIdAndTimeBetween(Long turbineId, LocalDateTime start, LocalDateTime end);

    List<ScadaRecord> findByTurbineIdInAndTimeBetween(Collection<Long> turbineIds, LocalDateTime start, LocalDateTime end);
}
