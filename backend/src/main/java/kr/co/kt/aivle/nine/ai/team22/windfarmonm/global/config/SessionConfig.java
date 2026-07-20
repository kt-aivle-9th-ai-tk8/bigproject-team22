package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Redis 기반 HttpSession 을 활성화한다. {@code RedisSessionRepository}({@code SessionRepository}) 빈을 등록하며,
 * 이후 HttpSession 은 자동으로 Redis 에 저장되어 멀티 인스턴스 간 공유된다.
 * maxInactiveIntervalInSeconds: 세션 만료 시간(초). 1800 = 30분.
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SessionConfig {
}
