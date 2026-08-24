package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

/**
 * 담당 풍력단지 통합조회의 단지 항목.
 * location/weather/power 쿼리 플래그가 꺼져 있으면 해당 필드는 null 로 채워진다.
 */
public record WindFarmSummaryResult(
        Long id,
        String name,
        Double latitude,
        Double longitude,
        Double capacity,
        WeatherInfo weather,
        PowerSummary power
) {
}
