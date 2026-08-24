package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.WindFarmDetailResult;

import java.util.List;

public record WindFarmDetailResponse(
        String id,
        String name,
        Double capacity,
        WeatherResponse weather,
        PowerResponse power,
        List<TurbineItemResponse> turbines
) {
    public static WindFarmDetailResponse from(WindFarmDetailResult result) {
        return new WindFarmDetailResponse(
                result.id().toString(),
                result.name(),
                result.capacity(),
                WeatherResponse.from(result.weather()),
                PowerResponse.from(result.power()),
                result.turbines().stream().map(TurbineItemResponse::from).toList()
        );
    }
}
