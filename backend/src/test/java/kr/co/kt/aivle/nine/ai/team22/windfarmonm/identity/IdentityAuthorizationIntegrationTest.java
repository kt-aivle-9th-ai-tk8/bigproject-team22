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
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired
    JdbcTemplate jdbc;

    private final RestClient client = RestClient.create();

    @BeforeEach
    void setUp() {
        // user 만 지우면 다른 테스트가 남긴 알림·감사기록이 FK 로 삭제를 막는다(테스트 순서에 따라 깨진다).
        // 삭제 순서는 IntegrationTestSupport 가 한 곳에서 관리한다.
        truncateAll(jdbc);
    }

    private String baseUrl() {
        return "http://localhost:" + environment.getProperty("local.server.port") + "/api";
    }

    private void seed(String employeeId, Role role) {
        seed(employeeId, role, null);
    }

    private void seed(String employeeId, Role role, String department) {
        userJpaRepository.save(User.create(employeeId, passwordEncoder.encode("pw12345!"), employeeId,
                "010-1234-5678", role, department));
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
    @DisplayName("가입 요청의 department 가 실제로 저장된다 — FE 폼이 받고도 버리던 값이다")
    void signUpStoresDepartment() {
        ResponseEntity<String> signUp = send(HttpMethod.POST, "/users",
                "{\"employee_id\":\"E2001\",\"password\":\"pw12345!\",\"user_name\":\"홍길동\","
                        + "\"phone\":\"010-1234-5678\",\"department\":\"운영팀\"}", null);

        assertThat(signUp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(userJpaRepository.findByEmployeeId("E2001").orElseThrow().getDepartment())
                .isEqualTo("운영팀");
    }

    @Test
    @DisplayName("빈 문자열 부서는 미입력으로 저장한다 — 공백을 넣어 두면 값이 있는 것처럼 보인다")
    void blankDepartmentIsStoredAsNull() {
        send(HttpMethod.POST, "/users",
                "{\"employee_id\":\"E2003\",\"password\":\"pw12345!\",\"user_name\":\"이영희\","
                        + "\"phone\":\"010-1234-5678\",\"department\":\"   \"}", null);

        assertThat(userJpaRepository.findByEmployeeId("E2003").orElseThrow().getDepartment()).isNull();
    }

    @Test
    @DisplayName("부서를 보내지 않아도 가입은 성공한다 — FE 가 payload 에 싣기 전까지 막지 않는다")
    void signUpWithoutDepartmentSucceeds() {
        ResponseEntity<String> response = send(HttpMethod.POST, "/users",
                "{\"employee_id\":\"E2002\",\"password\":\"pw12345!\",\"user_name\":\"김철수\","
                        + "\"phone\":\"010-1234-5678\"}", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("마이페이지: 사번·이름·전화번호가 마스킹되어 내려가고 이메일은 담기지 않는다")
    void myProfile_isMasked() {
        seed("MGR1", Role.MANAGER);
        String cookie = sessionCookie(login("MGR1"));

        ResponseEntity<String> response = send(HttpMethod.GET, "/users/mypage", null, cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"employee_id\":\"M**1\"")          // MGR1 → 가운데 마스킹
                .contains("\"user_name\":\"M**1\"")
                .contains("\"phone\":\"010-*****5678\"")       // 010-1234-5678
                .contains("\"role\":\"MANAGER\"")
                .contains("\"department\":null");                // 이 계정은 부서 미입력
        // 원문이 새지 않는지 — 마스킹은 '가리는 것'이지 '추가하는 것'이 아니다.
        assertThat(response.getBody())
                .doesNotContain("MGR1\"")
                .doesNotContain("010-1234-5678");
        // 스키마에 이메일이 없으므로 필드 자체가 없어야 한다(빈 값으로라도 내보내지 않는다).
        assertThat(response.getBody()).doesNotContain("email");
    }

    @Test
    @DisplayName("마이페이지의 부서는 마스킹하지 않는다 — 조직 정보이지 개인 식별정보가 아니다")
    void myProfile_showsDepartmentAsIs() {
        seed("MGR9", Role.MANAGER, "운영팀");
        String cookie = sessionCookie(login("MGR9"));

        assertThat(send(HttpMethod.GET, "/users/mypage", null, cookie).getBody())
                .contains("\"department\":\"운영팀\"");
    }

    @Test
    @DisplayName("마이페이지는 로그인해야 볼 수 있다 — 회원가입 경로(/users)만 공개다")
    void myProfile_requiresLogin() {
        assertThat(send(HttpMethod.GET, "/users/mypage", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
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
        // 사번은 마스킹되어 나간다("ADMIN1" → "AD**N1"). 마스킹 규칙 자체는 PiiMaskerTest 가 고정하므로
        // 여기서는 기대값을 리터럴로 박아 "이 API 응답에 원문이 실리지 않는다"만 못 박는다.
        assertThat(response.getBody())
                .contains("AD**N1")
                .doesNotContain("ADMIN1");
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

    @Test
    @DisplayName("가입 거절: ADMIN 이 GUEST 를 삭제하면 200 + 계정이 사라진다")
    void rejectSignUp_deletesGuest() {
        seed("ADMIN1", Role.ADMIN);
        seed("GUEST1", Role.GUEST);
        String cookie = sessionCookie(login("ADMIN1"));
        long guestId = userJpaRepository.findByEmployeeId("GUEST1").orElseThrow().getId();

        ResponseEntity<String> response = send(HttpMethod.DELETE, "/admin/users/" + guestId, null, cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userJpaRepository.findByEmployeeId("GUEST1")).isEmpty();
    }

    @Test
    @DisplayName("가입 거절: 이미 승인된 계정은 400(U003) — 계정은 그대로 남는다")
    void rejectSignUp_approvedUser_400() {
        seed("ADMIN1", Role.ADMIN);
        seed("MGR1", Role.MANAGER);
        String cookie = sessionCookie(login("ADMIN1"));
        long managerId = userJpaRepository.findByEmployeeId("MGR1").orElseThrow().getId();

        ResponseEntity<String> response = send(HttpMethod.DELETE, "/admin/users/" + managerId, null, cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("U003");
        assertThat(userJpaRepository.findByEmployeeId("MGR1")).isPresent();
    }

    @Test
    @DisplayName("가입 거절: ADMIN 이 아닌 사용자는 403 — 관리자 API 규약 그대로")
    void rejectSignUp_nonAdmin_403() {
        seed("MGR1", Role.MANAGER);
        seed("GUEST1", Role.GUEST);
        String cookie = sessionCookie(login("MGR1"));
        long guestId = userJpaRepository.findByEmployeeId("GUEST1").orElseThrow().getId();

        ResponseEntity<String> response = send(HttpMethod.DELETE, "/admin/users/" + guestId, null, cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userJpaRepository.findByEmployeeId("GUEST1")).isPresent();
    }
}
