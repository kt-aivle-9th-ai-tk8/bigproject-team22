package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.WeatherInfo;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.port.WeatherProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * {@link WeatherProvider} 포트의 기상청(KMA) API 어댑터(골격).
 * <p>
 * 발전소별 빈번 호출과 KMA API 호출 제한을 고려해 관측소별로 Redis 에 10분 TTL 로 캐싱한다.
 * 실제 KMA 응답 스키마는 미정이므로 파싱은 최소화(placeholder)했고, 호출/캐싱/구성 골격에 집중한다.
 * 스키마 확정 시 {@link #fetchFromKma} 만 채우면 된다.
 */
@Component
@EnableConfigurationProperties(KmaProperties.class)
public class KmaWeatherProvider implements WeatherProvider {

    private static final String CACHE_KEY_PREFIX = "weather:station:";
    private static final long CACHE_TTL_MINUTES = 10L;

    private final KmaProperties properties;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final RestClient restClient;

    public KmaWeatherProvider(KmaProperties properties, RedisTemplate<Object, Object> redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl() != null ? properties.baseUrl() : "")
                .build();
    }

    @Override
    public WeatherInfo getWeather(Long awsStationId, Long asosStationId) {
        Long stationId = (awsStationId != null) ? awsStationId : asosStationId;
        if (stationId == null) {
            return WeatherInfo.empty();
        }
        String cacheKey = CACHE_KEY_PREFIX + stationId;

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof WeatherInfo weatherInfo) {
            return weatherInfo;
        }

        WeatherInfo fetched = fetchFromKma(awsStationId, asosStationId);
        redisTemplate.opsForValue().set(cacheKey, fetched, Duration.ofMinutes(CACHE_TTL_MINUTES));
        return fetched;
    }

    /**
     * 기상청 API 를 호출해 실시간 날씨를 조회한다(골격).
     * 응답 스키마 미정이므로 현재는 호출만 수행하고 파싱 실패/미구현 시 빈 결과를 반환한다.
     */
    private WeatherInfo fetchFromKma(Long awsStationId, Long asosStationId) {
        try {
            if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
                return WeatherInfo.empty();
            }
            // TODO: KMA 실제 엔드포인트/쿼리파라미터/응답 스키마 확정 후 파싱 구현.
            //  예) restClient.get().uri(uri -> uri.path("/...").queryParam("authKey", properties.apiKey())
            //        .queryParam("stn", awsStationId).build()).retrieve().body(String.class) 파싱.
            restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("authKey", properties.apiKey())
                            .queryParam("stn", awsStationId != null ? awsStationId : asosStationId)
                            .build())
                    .retrieve()
                    .body(String.class);
            // placeholder: 스키마 확정 전까지 파싱 결과 없음
            return WeatherInfo.empty();
        } catch (RuntimeException e) {
            // 외부 API 오류가 관제 조회 전체를 실패시키지 않도록 빈 결과로 폴백
            return WeatherInfo.empty();
        }
    }
}
