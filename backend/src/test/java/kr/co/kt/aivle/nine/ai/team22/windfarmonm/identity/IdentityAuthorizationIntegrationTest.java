package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.infrastructure.UserJpaRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.support.IntegrationTestSupport;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인증/인가 인터셉터 체인과 1인 1세션 정책을 실제 HTTP·세션 쿠키·Redis 로 검증한다.
 * HTTP 클라이언트는 의존성 없는 spring-web 의 RestClient 를 사용하며, exchange 로 4xx 도 예외 없이 받는다.
 */
class IdentityAuthorizationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    UserJpaRepository userJpaRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    Environment environment;

    private final RestClient client = RestClient.create();

    @BeforeEach
    void setUp() {
        userJpaRepository.deleteAll();
    }

    private String baseUrl() {
        return "http://localhost:" + environment.getProperty("local.server.port") + "/api";
    }

    private void seed(String employeeId, Role role) {
        userJpaRepository.save(User.create(employeeId, passwordEncoder.encode("pw12345!"), employeeId, "010-1234-5678", role));
    }

    /** exchange 로 상태코드/헤더/본문을 예외 없이 그대로 받는다. */
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

    /** 요청 바디는 운영과 동일하게 snake_case 로 보낸다(FE 합의 계약). */
    private ResponseEntity<String> login(String employeeId) {
        return send(HttpMethod.POST, "/auth/login",
                "{\"employee_id\":\"" + employeeId + "\",\"password\":\"pw12345!\"}", null);
    }

    private String sessionCookie(ResponseEntity<String> response) {
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("SESSION="))
                .map(c -> c.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    private ResponseEntity<String> getAdminUsers(String cookie) {
        return send(HttpMethod.GET, "/admin/users", null, cookie);
    }

    @Test
    @DisplayName("회원가입은 항상 GUEST 로 생성된다")
    void signUp_createsGuest() {
        ResponseEntity<String> response = send(HttpMethod.POST, "/users",
                "{\"employee_id\":\"E1001\",\"password\":\"pw12345!\",\"user_name\":\"홍길동\",\"phone\":\"010-1234-5678\",\"role\":\"ADMIN\"}", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"role\":\"GUEST\"");
    }

    @Test
    @DisplayName("미인증 사용자의 관리자 API 접근은 401")
    void adminApi_unauthenticated_401() {
        assertThat(getAdminUsers(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("GUEST 는 로그인 자체가 차단된다(A004 승인 대기) — 세션을 발급하지 않는다")
    void guest_loginBlocked() {
        seed("GUEST1", Role.GUEST);

        ResponseEntity<String> response = login("GUEST1"); // 올바른 비밀번호라도 승인 전이면 차단

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("승인 대기");
        // 세션 쿠키가 발급되지 않아야 한다(로그인 실패)
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    @DisplayName("MANAGER 는 관리자 경로에서 403(권한 부족)")
    void adminApi_manager_403Denied() {
        seed("MGR1", Role.MANAGER);
        String cookie = sessionCookie(login("MGR1"));

        ResponseEntity<String> response = getAdminUsers(cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("접근 권한이 없습니다");
    }

    @Test
    @DisplayName("ADMIN 은 관리자 API 접근 가능(200)")
    void adminApi_admin_200() {
        seed("ADMIN1", Role.ADMIN);
        String cookie = sessionCookie(login("ADMIN1"));

        ResponseEntity<String> response = getAdminUsers(cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("ADMIN1");
    }

    @Test
    @DisplayName("1인 1세션: 재로그인하면 이전 세션은 축출되어 401, 새 세션만 유효")
    void singleSession_secondLoginEvictsFirst() {
        seed("MGR1", Role.MANAGER);
        String firstCookie = sessionCookie(login("MGR1"));
        String secondCookie = sessionCookie(login("MGR1"));

        // 이전 세션은 Redis 에서 축출됨 → 인증 실패(401)
        assertThat(getAdminUsers(firstCookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // 새 세션은 유효(인증됨) → 권한 부족(403), 즉 세션 자체는 살아있음
        assertThat(getAdminUsers(secondCookie).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
