package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.WeatherInfo;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.port.WeatherProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * {@link WeatherProvider} 어댑터. 기상청 지상관측 시간자료(ASOS, kma_sfctm2.php)로 대시보드 날씨를 조회한다.
 * <p>
 * 발전소별 빈번 호출/호출 제한을 고려해 ASOS 지점별 Redis 10분 TTL 캐싱을 병행한다(관측이 시간자료이므로 충분).
 * 조회 실패/무자료는 UNKNOWN 으로 폴백(과거 날씨 오인 방지)하며, 이 결과도 캐싱한다(의도적 — KMA 재호출 억제).
 * Redis 장애 시에도 단지 조회 전체를 실패시키지 않도록 캐시 접근을 감싼다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(KmaProperties.class)
public class KmaAsosWeatherProvider implements WeatherProvider {

    private static final String ASOS_HOURLY_PATH = "/kma_sfctm2.php";
    private static final String CACHE_KEY_PREFIX = "weather:asos:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final KmaProperties properties;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final RestClient restClient;

    public KmaAsosWeatherProvider(KmaProperties properties, RedisTemplate<Object, Object> redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl() != null ? properties.baseUrl() : "")
                .requestFactory(factory)
                .build();
    }

    @Override
    public WeatherInfo getWeather(Long asosStationId) {
        if (asosStationId == null) {
            return WeatherInfo.unknown();
        }
        String cacheKey = CACHE_KEY_PREFIX + asosStationId;

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof WeatherInfo weatherInfo) {
                return weatherInfo;
            }
        } catch (RuntimeException e) {
            log.warn("ASOS 날씨 캐시 조회 실패(stn={}): {}", asosStationId, e.getMessage());
        }

        WeatherInfo fetched = fetch(asosStationId);

        try {
            redisTemplate.opsForValue().set(cacheKey, fetched, CACHE_TTL);
        } catch (RuntimeException e) {
            log.warn("ASOS 날씨 캐시 저장 실패(stn={}): {}", asosStationId, e.getMessage());
        }
        return fetched;
    }

    /** KMA ASOS 조회 → 파싱 → 요청 지점의 최신 관측을 WeatherInfo 로. 실패/무자료는 UNKNOWN. */
    private WeatherInfo fetch(Long asosStationId) {
        if (isBlank(properties.baseUrl()) || isBlank(properties.apiKey())) {
            return WeatherInfo.unknown();
        }
        try {
            String body = restClient.get()
                    .uri(uri -> uri.path(ASOS_HOURLY_PATH)
                            .queryParam("stn", asosStationId)
                            .queryParam("help", 0)
                            .queryParam("authKey", properties.apiKey())
                            .build())
                    .retrieve()
                    .body(String.class);
            List<KmaAsosResponseParser.Reading> readings = KmaAsosResponseParser.parse(body);
            return readings.stream()
                    .filter(r -> asosStationId.equals(r.stationId()))
                    .max(Comparator.comparing(KmaAsosResponseParser.Reading::timestamp))
                    .map(r -> new WeatherInfo(r.weatherType(), r.temperature(), r.windSpeed()))
                    .orElseGet(() -> {
                        if (!isBlank(body)) {
                            // 응답은 왔으나 지점 관측 0건: authKey 만료/쿼터초과(200+에러본문) 또는 포맷 변경 신호.
                            log.warn("ASOS 응답에서 관측 0건(stn={}). authKey/포맷 회귀 가능성. 응답 일부: {}",
                                    asosStationId, snippet(body));
                        }
                        return WeatherInfo.unknown();
                    });
        } catch (RuntimeException e) {
            log.warn("ASOS 날씨 조회 실패(stn={}): {}", asosStationId, e.getMessage());
            return WeatherInfo.unknown();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 로그용 응답 스니펫(공백 정규화 + 최대 200자). */
    private static String snippet(String body) {
        String flat = body.strip().replaceAll("\\s+", " ");
        return flat.length() > 200 ? flat.substring(0, 200) + "…" : flat;
    }
}
