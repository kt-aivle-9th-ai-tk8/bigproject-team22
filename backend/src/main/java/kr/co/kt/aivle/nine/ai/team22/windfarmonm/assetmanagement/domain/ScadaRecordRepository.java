package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SCADA 계측 원천(scada_record) 저장소 포트. 순시/실시간(raw) 발전량 조회에 사용한다.
 */
public interface ScadaRecordRepository {

    /** 단일 터빈의 최신 계측 레코드(현재 출력용) */
    Optional<ScadaRecord> findLatestByTurbineId(Long turbineId);

    /** 단일 터빈의 기간 raw 계측 레코드 */
    List<ScadaRecord> findByTurbineIdAndTimeBetween(Long turbineId, LocalDateTime start, LocalDateTime end);

    /** 여러 터빈(=단지)의 기간 raw 계측 레코드 */
    List<ScadaRecord> findByTurbineIdsAndTimeBetween(List<Long> turbineIds, LocalDateTime start, LocalDateTime end);
}
