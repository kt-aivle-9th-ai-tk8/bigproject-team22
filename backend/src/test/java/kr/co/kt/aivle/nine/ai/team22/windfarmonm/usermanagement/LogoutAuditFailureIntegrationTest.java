package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.infrastructure.UserJpaRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.support.IntegrationTestSupport;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.domain.AuditLog;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.domain.AuditLogRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 감사 기록이 실패해도 <b>로그아웃은 완료되어야 한다</b>.
 * <p>
 * 다른 감사 지점은 기록 실패가 업무 처리를 되돌리지만, 로그아웃은 반대다 — 기록에 실패했다고 세션을
 * 살려 두면 사용자가 로그아웃했다고 믿는 계정이 열린 채 남는다. 이 예외를 코드가 아니라 동작으로 고정한다.
 * <p>
 * 저장소를 대역으로 바꾸므로 컨텍스트가 갈라진다. 그래서 다른 감사 검증과 클래스를 나눴다.
 */
class LogoutAuditFailureIntegrationTest extends IntegrationTestSupport {

    @MockitoBean
    AuditLogRepository auditLogRepository;

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
    @DisplayName("감사 기록이 실패해도 세션은 파기된다 — 로그아웃 실패로 계정이 열린 채 남지 않는다")
    void logoutInvalidatesSessionEvenIfAuditFails() {
        userJpaRepository.save(User.create("ADMIN1", passwordEncoder.encode("pw12345!"),
                "ADMIN1", "010-1234-5678", Role.ADMIN));
        String cookie = sessionCookie(send(HttpMethod.POST, "/auth/login",
                "{\"employee_id\":\"ADMIN1\",\"password\":\"pw12345!\"}", null));
        assertThat(send(HttpMethod.GET, "/admin/users", null, cookie).getStatusCode())
                .isEqualTo(HttpStatus.OK); // 세션이 살아 있는 상태에서 시작

        // 이 시점부터 감사 적재가 실패한다.
        given(auditLogRepository.save(any(AuditLog.class))).willThrow(new IllegalStateException("audit down"));

        ResponseEntity<String> logout = send(HttpMethod.POST, "/auth/logout", null, cookie);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK); // 세션은 실제로 파기됐으므로 실패라 답하지 않는다
        assertThat(send(HttpMethod.GET, "/admin/users", null, cookie).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED); // 세션이 죽었다
    }

    private String baseUrl() {
        return "http://localhost:" + environment.getProperty("local.server.port") + "/api";
    }

    private String sessionCookie(ResponseEntity<String> response) {
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
                .headers(response.getHeaders()) // Set-Cookie 를 봐야 세션 쿠키를 얻는다
                .body(response.bodyTo(String.class)));
    }
}
