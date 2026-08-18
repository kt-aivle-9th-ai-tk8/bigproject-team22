package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import com.sun.net.httpserver.HttpServer;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.WeatherInfo;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.WeatherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ASOS 날씨 어댑터의 실제 HTTP 왕복을 JDK 내장 HttpServer 로 검증한다.
 * <p>
 * MockRestServiceServer 를 쓰지 않는 이유: 어댑터가 빌더에 {@code requestFactory} 를 직접 지정하는데
 * 바인딩이 그 뒤에 덮여 가로채지 못한다. 실서버를 띄우면 <b>실제로 나가는 쿼리 파라미터</b>까지 볼 수 있어
 * 오히려 낫다 — 이 통합의 위험이 정확히 "요청 인자가 KMA 가 기대하는 형태인가" 였다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // 캐시 미접근 경로가 있는 테스트가 섞여 있다
class KmaAsosWeatherProviderTest {

    /** 실제 ASOS 응답 행(지점 156, WW="-"): WS[3]=2.1, TA[11]=28.4 */
    private static final String ROW_156 =
            "202608031500 156   2  2.1  -9 -9.0   -9 1010.1 1012.1  7  -0.2  28.4  25.6  85.0  32.8   -9.0   -9.0   -9.0   -9.0   -9.0   -9.0   -9.0 -9 -9 -                        6   6    4 -         -9  -9  -9  3300  0.0  1.20 -9  36.6 -99.0 -99.0 -99.0 -99.0  -9 -9.0 -9  3 -9";

    @Mock
    RedisTemplate<Object, Object> redisTemplate;
    @Mock
    ValueOperations<Object, Object> valueOperations;

    private HttpServer server;
    private final List<String> receivedQueries = new ArrayList<>();
    private String responseBody = "";

    @BeforeEach
    void startServer() throws IOException {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/kma_sfctm2.php", exchange -> {
            receivedQueries.add(exchange.getRequestURI().getQuery());
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private KmaAsosWeatherProvider provider(String baseUrl, String apiKey) {
        return new KmaAsosWeatherProvider(
                new KmaProperties(apiKey, baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(5)),
                redisTemplate,
                RestClient.builder());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    @DisplayName("정상 응답: 최신 관측을 WeatherInfo 로 옮기고 stn/help/authKey 를 실어 보낸다")
    void fetchesLatestReading() {
        responseBody = "#START7777\n# YYMMDDHHMI STN ...\n" + ROW_156 + "\n#7777END";

        WeatherInfo info = provider(baseUrl(), "test-key").getWeather(156L);

        assertThat(info).isEqualTo(new WeatherInfo(WeatherType.CLEAR, 28.4, 2.1));
        assertThat(receivedQueries).hasSize(1);
        assertThat(receivedQueries.getFirst())
                .contains("stn=156")
                .contains("help=0")
                .contains("authKey=test-key");
    }

    @Test
    @DisplayName("성공한 조회만 캐싱한다 — 실패를 캐싱하면 UNKNOWN 이 10분간 굳는다")
    void cachesOnlySuccess() {
        responseBody = ROW_156;
        provider(baseUrl(), "test-key").getWeather(156L);

        verify(valueOperations).set(eq("weather:asos:156"), any(), any(Duration.class));
    }

    @Test
    @DisplayName("요청한 지점의 관측이 0건이면 UNKNOWN 이고 캐싱하지 않는다(다음 요청이 곧 재시도)")
    void unknownWhenStationMissingFromResponse() {
        responseBody = ROW_156; // 156 만 담긴 응답인데 260 을 물어본다

        WeatherInfo info = provider(baseUrl(), "test-key").getWeather(260L);

        assertThat(info).isEqualTo(WeatherInfo.unknown());
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("설정(base-url/api-key)이 비면 HTTP 를 아예 때리지 않고 UNKNOWN")
    void skipsHttpWhenNotConfigured() {
        assertThat(provider("", "").getWeather(156L)).isEqualTo(WeatherInfo.unknown());
        assertThat(provider(baseUrl(), "").getWeather(156L)).isEqualTo(WeatherInfo.unknown());
        assertThat(provider("", "test-key").getWeather(156L)).isEqualTo(WeatherInfo.unknown());

        assertThat(receivedQueries).isEmpty();
    }

    @Test
    @DisplayName("지점번호가 없는 단지는 조회하지 않는다")
    void skipsHttpWhenStationIdNull() {
        assertThat(provider(baseUrl(), "test-key").getWeather(null)).isEqualTo(WeatherInfo.unknown());
        assertThat(receivedQueries).isEmpty();
    }

    @Test
    @DisplayName("캐시에 값이 있으면 KMA 를 호출하지 않는다")
    void servesFromCache() {
        WeatherInfo cached = new WeatherInfo(WeatherType.RAIN, 19.5, 4.2);
        given(valueOperations.get("weather:asos:156")).willReturn(cached);

        assertThat(provider(baseUrl(), "test-key").getWeather(156L)).isEqualTo(cached);
        assertThat(receivedQueries).isEmpty();
    }

    @Test
    @DisplayName("Redis 가 죽어도 단지 조회를 실패시키지 않는다 — 캐시를 건너뛰고 직접 조회한다")
    void survivesRedisFailure() {
        given(valueOperations.get(any())).willThrow(new IllegalStateException("redis down"));
        willThrow(new IllegalStateException("redis down"))
                .given(valueOperations).set(any(), any(), any(Duration.class));
        responseBody = ROW_156;

        assertThat(provider(baseUrl(), "test-key").getWeather(156L))
                .isEqualTo(new WeatherInfo(WeatherType.CLEAR, 28.4, 2.1));
    }
}
