package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * SCADA 계측 원천(scada_record) 저장소 포트. 순시/실시간(raw) 발전량 조회에 사용한다.
 */
public interface ScadaRecordRepository {

    /**
     * 여러 터빈의 <b>지정 시각</b> 계측 레코드(현재 출력용).
     * <p>
     * '가장 마지막 행'을 찾지 않고 조회할 시각을 지정한다 — SCADA 적재가 정시 단위이므로 (turbine_id, recorded_at)
     * 복합 PK 에 대한 직접 조회가 되어 비용이 이력 크기와 무관해지고, 오래된 값이 현재 출력으로 둔갑하지 않는다.
     * 일/월 집계 조회가 이미 쓰는 방식과도 일관된다.
     */
    List<ScadaRecord> findByTurbineIdsAndTimeIn(Collection<Long> turbineIds, Collection<LocalDateTime> times);

    /** 단일 터빈의 기간 raw 계측 레코드 */
    List<ScadaRecord> findByTurbineIdAndTimeBetween(Long turbineId, LocalDateTime start, LocalDateTime end);

    /** 여러 터빈(=단지)의 기간 raw 계측 레코드 */
    List<ScadaRecord> findByTurbineIdsAndTimeBetween(List<Long> turbineIds, LocalDateTime start, LocalDateTime end);
}
