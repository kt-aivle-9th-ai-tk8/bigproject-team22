package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.WeatherType;

import java.io.Serializable;

/**
 * 대시보드 실시간 날씨(ASOS 조회 결과). Redis 캐시 직렬화 대상이므로 {@link Serializable}.
 * 조회 실패/무자료 시 {@link #unknown()} 을 사용한다(값 필드는 null, 유형은 UNKNOWN).
 */
public record WeatherInfo(
        WeatherType weatherType,
        Double temperature,
        Double windSpeed
) implements Serializable {

    /** 조회 실패/무자료: 사용자가 과거 날씨로 오인하지 않도록 UNKNOWN 으로 반환한다. */
    public static WeatherInfo unknown() {
        return new WeatherInfo(WeatherType.UNKNOWN, null, null);
    }
}
