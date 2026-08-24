package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.WindFarmSummaryResult;

/**
 * 담당 풍력단지 통합조회 항목 응답. location/weather/power 미포함 시 해당 필드는 null.
 */
public record WindFarmSummaryResponse(
        String id,
        String name,
        Double latitude,
        Double longitude,
        Double capacity,
        WeatherResponse weather,
        PowerResponse power
) {
    public static WindFarmSummaryResponse from(WindFarmSummaryResult result) {
        return new WindFarmSummaryResponse(
                result.id().toString(),
                result.name(),
                result.latitude(),
                result.longitude(),
                result.capacity(),
                WeatherResponse.from(result.weather()),
                PowerResponse.from(result.power())
        );
    }
}
