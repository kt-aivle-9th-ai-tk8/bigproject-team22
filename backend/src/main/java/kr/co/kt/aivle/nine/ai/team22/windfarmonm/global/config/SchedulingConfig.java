package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 + 분산 락 설정.
 * <p>
 * 애플리케이션은 ECS 서비스로 <b>여러 인스턴스가 동시에</b> 뜬다. {@code @Scheduled} 는 인스턴스마다 독립적으로
 * 돌기 때문에 아무 장치가 없으면 매시각 배치가 인스턴스 수만큼 중복 실행된다(추론 중복 호출, 이벤트/알림 중복 생성).
 * ShedLock 이 Redis 에 락을 잡아 한 회차를 한 인스턴스만 수행하게 만든다.
 * <p>
 * 락 저장소로 Redis 를 쓰는 이유는 이미 세션 저장소로 확보되어 있어 추가 인프라가 필요 없기 때문이다.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulingConfig {

    /** 락 키 접두어. 다른 환경(스테이징 등)이 같은 Redis 를 공유해도 서로의 락을 침범하지 않게 한다. */
    private static final String LOCK_KEY_PREFIX = "windfarmonm";

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, LOCK_KEY_PREFIX);
    }
}
