package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.port;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.WeatherInfo;

/**
 * 실시간 날씨 조회 포트. 어댑터는 기상청(KMA) API 를 호출하며, 발전소별 빈번 호출과
 * API 제한을 고려해 관측소별로 Redis 에 TTL 캐싱한다.
 */
public interface WeatherProvider {

    /**
     * 관측소 지점번호로 실시간 날씨를 조회한다. 데이터가 없으면 필드가 null 인 결과를 반환할 수 있다.
     *
     * @param awsStationId  기상청 AWS 지점번호(nullable)
     * @param asosStationId 기상청 ASOS 지점번호(nullable)
     */
    WeatherInfo getWeather(Long awsStationId, Long asosStationId);
}
