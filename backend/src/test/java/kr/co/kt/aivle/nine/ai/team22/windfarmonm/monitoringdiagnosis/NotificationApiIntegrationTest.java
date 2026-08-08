package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.infrastructure.UserJpaRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.Notification;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.NotificationRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알림 API 계약을 실제 HTTP 로 검증한다. 자기 알림만 다루고, 타인 알림은 404(존재 은닉).
 */
class NotificationApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    UserJpaRepository userJpaRepository;
    @Autowired
    NotificationRepository notificationRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    Environment environment;
    @Autowired
    JdbcTemplate jdbc;

    private final RestClient client = RestClient.create();

    private long ownerId;

    @BeforeEach
    void setUp() {
        truncateAll(jdbc);
        ownerId = seedUser("MGR1");
        seedUser("MGR2"); // 타인
    }

    @AfterEach
    void tearDown() {
        truncateAll(jdbc);
    }

    private long seedUser(String employeeId) {
        return userJpaRepository
                .save(User.create(employeeId, passwordEncoder.encode("pw12345!"), employeeId, "010-1234-5678", Role.MANAGER))
                .getId();
    }

    private long seedNotification(long userId, String title, boolean read) {
        Notification n = Notification.of(userId, null, title); // report_id null(FK nullable) — 시드 단순화
        if (read) {
            n.markRead();
        }
        return notificationRepository.save(n).getId();
    }

    private String baseUrl() {
        return "http://localhost:" + environment.getProperty("local.server.port") + "/api";
    }

    private ResponseEntity<String> send(HttpMethod method, String path, String cookie) {
        RestClient.RequestBodySpec spec = client.method(method).uri(baseUrl() + path);
        if (cookie != null) {
            spec.header(HttpHeaders.COOKIE, cookie);
        }
        return spec.exchange((request, response) -> ResponseEntity
                .status(response.getStatusCode())
                .body(response.bodyTo(String.class)));
    }

    private String loginCookie(String employeeId) {
        ResponseEntity<String> response = client.method(HttpMethod.POST).uri(baseUrl() + "/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"employee_id\":\"" + employeeId + "\",\"password\":\"pw12345!\"}")
                .retrieve().toEntity(String.class);
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("SESSION="))
                .map(c -> c.split(";", 2)[0])
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("목록은 내 알림만 반환하고(문자열 id·is_read), unread 필터가 동작한다")
    void getNotifications_ownScopeAndUnreadFilter() {
        seedNotification(ownerId, "읽지않은알림", false);
        seedNotification(ownerId, "읽은알림", true);
        String cookie = loginCookie("MGR1");

        String all = send(HttpMethod.GET, "/notifications", cookie).getBody();
        assertThat(all).contains("읽지않은알림").contains("읽은알림")
                .contains("\"id\":\"").contains("\"is_read\":");

        String unread = send(HttpMethod.GET, "/notifications?unread=true", cookie).getBody();
        assertThat(unread).contains("읽지않은알림").doesNotContain("읽은알림");
    }

    @Test
    @DisplayName("읽음 처리하면 unread 목록에서 빠진다(멱등)")
    void markRead() {
        long id = seedNotification(ownerId, "알림", false);
        String cookie = loginCookie("MGR1");

        assertThat(send(HttpMethod.PATCH, "/notifications/" + id + "/read", cookie).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // 다시 호출해도 안전(멱등)
        assertThat(send(HttpMethod.PATCH, "/notifications/" + id + "/read", cookie).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(send(HttpMethod.GET, "/notifications?unread=true", cookie).getBody())
                .doesNotContain("알림");
    }

    @Test
    @DisplayName("삭제하면 목록에서 사라진다")
    void deleteNotification() {
        long id = seedNotification(ownerId, "지울알림", false);
        String cookie = loginCookie("MGR1");

        assertThat(send(HttpMethod.DELETE, "/notifications/" + id, cookie).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(send(HttpMethod.GET, "/notifications", cookie).getBody())
                .doesNotContain("지울알림");
    }

    @Test
    @DisplayName("타인 알림은 읽음/삭제 시 404(존재 은닉)")
    void othersNotification_404() {
        long ownerNoti = seedNotification(ownerId, "주인알림", false);
        String otherCookie = loginCookie("MGR2");

        assertThat(send(HttpMethod.PATCH, "/notifications/" + ownerNoti + "/read", otherCookie).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(HttpMethod.DELETE, "/notifications/" + ownerNoti, otherCookie).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
