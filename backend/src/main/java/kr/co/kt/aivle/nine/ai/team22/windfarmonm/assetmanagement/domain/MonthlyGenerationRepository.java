package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 월별 발전량(monthly_generation) 저장소 포트. time 은 해당 월 1일 00:00 기준이다.
 */
public interface MonthlyGenerationRepository {

    /** 단일 터빈의 특정 월(1일) 발전량 */
    Optional<MonthlyGeneration> findByTurbineIdAndTime(Long turbineId, LocalDateTime time);

    /** 여러 터빈(=단지)의 특정 월(1일) 발전량 */
    List<MonthlyGeneration> findByTurbineIdsAndTime(List<Long> turbineIds, LocalDateTime time);

    /** 단일 터빈의 기간 월별 발전량(경계 포함) */
    List<MonthlyGeneration> findByTurbineIdAndTimeBetween(Long turbineId, LocalDateTime start, LocalDateTime end);

    /** 여러 터빈(=단지)의 기간 월별 발전량(경계 포함) */
    List<MonthlyGeneration> findByTurbineIdsAndTimeBetween(List<Long> turbineIds, LocalDateTime start, LocalDateTime end);
}
