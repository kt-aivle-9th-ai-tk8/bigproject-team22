package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

import java.util.List;

/**
 * 단일 풍력단지 상세조회 결과.
 */
public record WindFarmDetailResult(
        Long id,
        String name,
        Double capacity,
        WeatherInfo weather,
        PowerSummary power,
        List<TurbineSummaryResult> turbines
) {
}
