package kr.co.kt.aivle.nine.ai.team22.windfarmonm.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

/**
 * 통합 테스트 공통 베이스. MySQL/Redis 컨테이너를 static 싱글턴으로 띄우고
 * {@code @ServiceConnection} 으로 spring.datasource.* / spring.data.redis.* 를 자동 주입한다.
 * <p>
 * - 개발용 compose 와 동일 이미지(mysql:8.4, redis:7-alpine), 포트는 랜덤 매핑되어 충돌 없음.
 * - MySQLContainer 는 JDBC 접속 성공을 기다리므로 별도 대기 전략이 불필요하다.
 * - 컨테이너를 수동 start 하고 stop 하지 않아(@Testcontainers 미사용) reuse 가 가능하다.
 *   참고) ~/.testcontainers.properties 의 testcontainers.reuse.enable=true 가 있으면 실행 간 재사용(CI 는 자동 무시).
 * <p>
 * test 프로파일은 main 의 application.yaml <b>위에 덧씌워진다</b> — 운영과 동일한 JSON 계약
 * (snake_case, null 포함)으로 테스트해야 계약 회귀를 잡을 수 있기 때문이다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    @ServiceConnection
    static final MySQLContainer MYSQL =
            new MySQLContainer(DockerImageName.parse("mysql:8.4")).withReuse(true);

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    static {
        MYSQL.start();
        REDIS.start();
    }

    /**
     * 데이터 테이블을 <b>FK 역순으로</b> 비운다. 깨끗한 상태에서 시작해야 하는 테스트가
     * {@code @BeforeEach} 에서 호출한다.
     * <p>
     * 각 테스트가 자기가 쓰는 테이블만 지우던 방식은 새 FK 가 생길 때마다 조용히 깨진다 —
     * 남은 자식 행이 다른 테스트의 부모 행 삭제를 막기 때문이다.
     * 삭제 순서를 여기 한 곳에서만 관리한다. <b>테이블을 추가하면 이 목록에도 넣을 것.</b>
     */
    protected static void truncateAll(JdbcTemplate jdbc) {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    /** 참조하는 쪽이 먼저 온다. */
    private static final List<String> TABLES_IN_DELETE_ORDER = List.of(
            "anomaly_event",
            "assignment",
            "monthly_generation",
            "daily_generation",
            "scada_record",
            "blade",
            "turbine",
            "wind_farm",
            "turbine_model",
            "`user`" // user 는 예약어라 DELETE FROM 시 백틱 필요
    );
}
