package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

import java.io.Serializable;

/**
 * 실시간 날씨 정보(기상청 API 조회 결과). Redis 캐시 직렬화 대상이므로 {@link Serializable}.
 * 데이터가 없으면 각 필드는 null 이 될 수 있다.
 */
public record WeatherInfo(
        String weatherType,
        Double temperature,
        Double windSpeed
) implements Serializable {

    public static WeatherInfo empty() {
        return new WeatherInfo(null, null, null);
    }
}
