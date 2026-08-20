package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.infrastructure.UserJpaRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 감사 로그가 실제 HTTP 요청으로 남는지 검증한다.
 * <p>
 * 개인정보 접속기록(고시 제8조)은 <b>누락되면 그 자체가 결함</b>이라, 단위 테스트로 발행만 확인하지 않고
 * 요청 → 적재까지 통째로 본다. 접속지(IP)가 채워지는지도 여기서만 확인할 수 있다.
 */
class AuditLogIntegrationTest extends IntegrationTestSupport {

    @Autowired
    UserJpaRepository userJpaRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    Environment environment;
    @Autowired
    JdbcTemplate jdbc;

    private final RestClient client = RestClient.create();

    @BeforeEach
    void setUp() {
        truncateAll(jdbc);
    }

    @AfterEach
    void tearDown() {
        truncateAll(jdbc);
    }

    @Test
    @DisplayName("로그인 성공/실패가 접속기록으로 남고 접속지 IP 가 채워진다")
    void loginAndFailureAreRecorded() {
        long userId = seed("MGR1", Role.MANAGER);

        login("MGR1", "pw12345!");
        login("MGR1", "wrong-password!");

        List<Map<String, Object>> logs = logsOf(userId);
        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(row -> row.get("action_type"))
                .containsExactlyInAnyOrder("LOGIN", "LOGIN_FAILED");
        // 접속지 정보가 비면 접속기록 요건을 못 채운다 — 컬럼만 있고 안 채우는 상태를 막는다.
        assertThat(logs).allSatisfy(row -> assertThat(row.get("ip_address")).asString().isNotBlank());
    }

    @Test
    @DisplayName("사번이 실재하지 않는 로그인 시도는 남기지 않는다 — 붙일 주체가 없다")
    void unknownEmployeeIdIsNotRecorded() {
        login("NOBODY", "pw12345!");

        assertThat(countAll()).isZero();
    }

    @Test
    @DisplayName("관리자의 회원목록 조회도 접속기록 대상이다(개인정보 '조회')")
    void userListViewIsRecorded() {
        long adminId = seed("ADMIN1", Role.ADMIN);
        String cookie = sessionCookie(login("ADMIN1", "pw12345!"));

        send(HttpMethod.GET, "/admin/users", null, cookie);

        assertThat(actionsOf(adminId)).contains("USER_LIST_VIEW");
    }

    @Test
    @DisplayName("권한 변경은 주체(관리자)와 대상(사용자)을 함께 남긴다")
    void roleChangeRecordsActorAndTarget() {
        long adminId = seed("ADMIN1", Role.ADMIN);
        long targetId = seed("GUEST1", Role.GUEST);
        String cookie = sessionCookie(login("ADMIN1", "pw12345!"));

        ResponseEntity<String> response = send(HttpMethod.PATCH, "/admin/users/" + targetId,
                "{\"role\":\"MANAGER\"}", cookie);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> log = logsOf(adminId).stream()
                .filter(row -> "USER_ROLE_CHANGE".equals(row.get("action_type")))
                .findFirst()
                .orElseThrow();
        assertThat(log.get("target_table")).isEqualTo("user");
        assertThat(((Number) log.get("target_id")).longValue()).isEqualTo(targetId);
    }

    @Test
    @DisplayName("가입 거절로 계정이 지워져도 그 계정의 접속기록은 남는다 — 보존기간이 계정 수명보다 길다")
    void accessRecordSurvivesAccountDeletion() {
        long adminId = seed("ADMIN1", Role.ADMIN);
        long guestId = seed("GUEST1", Role.GUEST);
        // 가입 직후 로그인을 시도한 GUEST — 접속기록이 남는다(A004 로 거부되지만 시도는 기록 대상).
        login("GUEST1", "pw12345!");
        assertThat(actionsOf(guestId)).contains("LOGIN_FAILED");

        String cookie = sessionCookie(login("ADMIN1", "pw12345!"));
        ResponseEntity<String> response = send(HttpMethod.DELETE, "/admin/users/" + guestId, null, cookie);

        // 기록이 계정 삭제를 막으면 안 된다(FK 를 두면 여기서 409 로 실패한다).
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userJpaRepository.findById(guestId)).isEmpty();
        // 계정은 사라졌지만 그 계정이 주체인 접속기록은 그대로 있어야 한다.
        assertThat(actionsOf(guestId)).contains("LOGIN_FAILED");
        assertThat(actionsOf(adminId)).contains("USER_REJECT");
    }

    private long seed(String employeeId, Role role) {
        return userJpaRepository
                .save(User.create(employeeId, passwordEncoder.encode("pw12345!"), employeeId, "010-1234-5678", role))
                .getId();
    }

    private List<Map<String, Object>> logsOf(long userId) {
        return jdbc.queryForList("SELECT action_type, target_table, target_id, ip_address"
                + " FROM audit_log WHERE user_id = ? ORDER BY log_id", userId);
    }

    private List<String> actionsOf(long userId) {
        return jdbc.queryForList("SELECT action_type FROM audit_log WHERE user_id = ? ORDER BY log_id",
                String.class, userId);
    }

    private Integer countAll() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
    }

    private String baseUrl() {
        return "http://localhost:" + environment.getProperty("local.server.port") + "/api";
    }

    private ResponseEntity<String> login(String employeeId, String password) {
        return send(HttpMethod.POST, "/auth/login",
                "{\"employee_id\":\"" + employeeId + "\",\"password\":\"" + password + "\"}", null);
    }

    private String sessionCookie(ResponseEntity<String> response) {
        // get() 은 헤더가 없으면 null 을 준다 — 로그인이 실패하면 원인을 알기 어려운 NPE 로 끝난다.
        return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("SESSION="))
                .map(c -> c.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    private ResponseEntity<String> send(HttpMethod method, String path, String jsonBody, String cookie) {
        RestClient.RequestBodySpec spec = client.method(method).uri(baseUrl() + path);
        if (jsonBody != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(jsonBody);
        }
        if (cookie != null) {
            spec.header(HttpHeaders.COOKIE, cookie);
        }
        return spec.exchange((request, response) -> ResponseEntity
                .status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.bodyTo(String.class)));
    }
}
