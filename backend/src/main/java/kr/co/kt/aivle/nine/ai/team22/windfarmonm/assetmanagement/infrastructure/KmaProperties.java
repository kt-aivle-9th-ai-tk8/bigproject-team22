package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 기상청(KMA) API 접속 설정. application yaml 의 {@code kma.*} 프로퍼티로 주입된다.
 * connect/read 타임아웃은 KMA 전용 fail-fast 요구라 전역 {@code spring.http.clients.*} 가 아닌 {@code kma.*} 로 한정한다
 * (SCADA 등 성질이 다른 외부 클라이언트가 전역값을 상속하지 않도록).
 */
@ConfigurationProperties(prefix = "kma")
public record KmaProperties(
        String apiKey,
        String baseUrl,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout
) {
}
