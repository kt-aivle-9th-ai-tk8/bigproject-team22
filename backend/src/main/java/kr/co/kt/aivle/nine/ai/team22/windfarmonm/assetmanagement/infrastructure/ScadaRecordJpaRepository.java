package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.ScadaRecord;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.TurbineInstantId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 발전량 원천(scada_record) 조회. {@code ScadaRecordRepositoryImpl} 어댑터에서만 사용한다.
 */
public interface ScadaRecordJpaRepository extends JpaRepository<ScadaRecord, TurbineInstantId> {

    /**
     * 지정한 시각들의 계측 레코드(현재 출력용).
     * <p>
     * 복합 PK (turbine_id, recorded_at) 에 대한 직접 조회라 비용이 <b>터빈 수 × 시각 수</b>로 고정된다
     * ('가장 마지막 행'을 MAX 로 찾던 이전 방식은 대상 터빈의 전 이력을 훑어 누적량에 비례했다).
     */
    List<ScadaRecord> findByTurbineIdInAndTimeIn(Collection<Long> turbineIds, Collection<LocalDateTime> times);

    List<ScadaRecord> findByTurbineIdAndTimeBetween(Long turbineId, LocalDateTime start, LocalDateTime end);

    List<ScadaRecord> findByTurbineIdInAndTimeBetween(Collection<Long> turbineIds, LocalDateTime start, LocalDateTime end);
}
