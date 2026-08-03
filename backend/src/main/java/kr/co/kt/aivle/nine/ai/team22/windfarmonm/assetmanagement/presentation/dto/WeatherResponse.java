package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.WeatherInfo;

public record WeatherResponse(
        String weatherType,
        Double temperature,
        Double windSpeed
) {
    /** null 인 경우 그대로 null 을 반환한다(플래그가 꺼져 미포함인 경우). */
    public static WeatherResponse from(WeatherInfo info) {
        if (info == null) {
            return null;
        }
        return new WeatherResponse(info.weatherType(), info.temperature(), info.windSpeed());
    }
}
