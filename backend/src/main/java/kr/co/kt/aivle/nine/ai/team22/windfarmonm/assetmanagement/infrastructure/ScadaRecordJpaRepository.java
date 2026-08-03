package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.ScadaRecord;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineInstantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 발전량 원천(scada_record) 조회. {@code ScadaRecordRepositoryImpl} 어댑터에서만 사용한다.
 */
public interface ScadaRecordJpaRepository extends JpaRepository<ScadaRecord, TurbineInstantId> {

    Optional<ScadaRecord> findTopByTurbineIdOrderByTimeDesc(Long turbineId);

    /** 터빈별 최신 1건(turbine_id, recorded_at 복합 PK 인덱스 활용). */
    @Query("""
            SELECT s FROM ScadaRecord s
            WHERE s.turbineId IN :turbineIds
              AND s.time = (SELECT MAX(s2.time) FROM ScadaRecord s2 WHERE s2.turbineId = s.turbineId)
            """)
    List<ScadaRecord> findLatestByTurbineIds(@Param("turbineIds") Collection<Long> turbineIds);

    List<ScadaRecord> findByTurbineIdAndTimeBetween(Long turbineId, LocalDateTime start, LocalDateTime end);

    List<ScadaRecord> findByTurbineIdInAndTimeBetween(Collection<Long> turbineIds, LocalDateTime start, LocalDateTime end);
}
