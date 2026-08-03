package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 일별 발전량(daily_generation) 저장소 포트. time 은 해당 일 00:00 기준이다.
 */
public interface DailyGenerationRepository {

    /** 단일 터빈의 특정 일자 발전량 */
    Optional<DailyGeneration> findByTurbineIdAndTime(Long turbineId, LocalDateTime time);

    /** 여러 터빈(=단지)의 특정 일자 발전량 */
    List<DailyGeneration> findByTurbineIdsAndTime(List<Long> turbineIds, LocalDateTime time);

    /** 단일 터빈의 기간 일별 발전량(경계 포함) */
    List<DailyGeneration> findByTurbineIdAndTimeBetween(Long turbineId, LocalDateTime start, LocalDateTime end);

    /** 여러 터빈(=단지)의 기간 일별 발전량(경계 포함) */
    List<DailyGeneration> findByTurbineIdsAndTimeBetween(List<Long> turbineIds, LocalDateTime start, LocalDateTime end);
}
