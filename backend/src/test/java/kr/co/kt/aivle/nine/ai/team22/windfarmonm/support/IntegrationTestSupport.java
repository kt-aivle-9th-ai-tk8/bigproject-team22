package kr.co.kt.aivle.nine.ai.team22.windfarmonm.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 공통 베이스. MySQL/Redis 컨테이너를 <b>static 싱글턴</b>으로 띄워
 * 모든 통합 테스트가 하나의 컨테이너 세트와 하나의 스프링 컨텍스트를 공유하게 한다.
 * <p>
 * - 개발용 compose 와 동일 이미지(mysql:8.4, redis:7-alpine), 포트는 랜덤 매핑되어 충돌 없음.
 * - withReuse(true): ~/.testcontainers.properties 에 testcontainers.reuse.enable=true 가 있으면
 *   테스트 실행 간에도 컨테이너를 재사용(CI 에서는 자동 무시).
 * - Testcontainers 2.x 는 core 만 사용하고 GenericContainer + @DynamicPropertySource 로 연결한다
 *   (DB 전용 모듈/@ServiceConnection 미사용).
 */
@SpringBootTest
public abstract class IntegrationTestSupport {

    private static final GenericContainer<?> MYSQL =
            new GenericContainer<>(DockerImageName.parse("mysql:8.4"))
                    .withExposedPorts(3306)
                    .withEnv("MYSQL_ROOT_PASSWORD", "root")
                    .withEnv("MYSQL_DATABASE", "windfarmonm")
                    .withEnv("MYSQL_USER", "test")
                    .withEnv("MYSQL_PASSWORD", "test")
                    // 포트 리스닝만으론 부족(초기화 중 재기동) → 로그로 준비 완료 대기
                    .waitingFor(Wait.forLogMessage(".*ready for connections.*", 2))
                    .withReuse(true);

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerConnectionProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                        + "/windfarmonm?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
