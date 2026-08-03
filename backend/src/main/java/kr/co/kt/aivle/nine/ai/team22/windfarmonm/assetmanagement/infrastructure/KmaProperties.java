package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기상청(KMA) API 접속 설정. application yaml 의 {@code kma.*} 프로퍼티로 주입된다.
 */
@ConfigurationProperties(prefix = "kma")
public record KmaProperties(
        String apiKey,
        String baseUrl
) {
}
