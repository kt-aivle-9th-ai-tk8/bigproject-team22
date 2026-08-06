package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.port;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.WeatherInfo;

/**
 * 대시보드 실시간 날씨 조회 포트. 어댑터는 기상청 지상관측 시간자료(ASOS, kma_sfctm2.php)를 호출하며,
 * 발전소별 빈번 호출과 API 제한을 고려해 관측소별로 Redis 에 TTL 캐싱한다.
 * (AWS 흐름은 적재→ML 용도로 분리되어 있으며 대시보드 날씨와 무관하다.)
 */
public interface WeatherProvider {

    /**
     * ASOS 관측소 지점번호로 대시보드 실시간 날씨를 조회한다.
     * 조회 실패/무자료 시 {@link WeatherInfo#unknown()} 을 반환한다(예외를 전파하지 않는다).
     *
     * @param asosStationId 기상청 ASOS 지점번호(nullable)
     */
    WeatherInfo getWeather(Long asosStationId);
}
