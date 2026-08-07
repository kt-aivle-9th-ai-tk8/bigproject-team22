package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.presentation.dto.UpdateReportRequest;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보고서 API 계약을 실제 HTTP 로 검증한다.
 * <p>
 * 자산(단지/터빈)은 읽기 전용 엔티티라 팩토리가 없으므로 SQL 로 직접 시드한다.
 */
class ReportApiIntegrationTest extends IntegrationTestSupport {

    private static final String PERIOD = "\"period_start\":\"2026-07-01T00:00:00\",\"period_end\":\"2026-07-31T23:00:00\"";

    @Autowired
    UserJpaRepository userJpaRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    Environment environment;
    @Autowired
    JdbcTemplate jdbc;

    private final RestClient client = RestClient.create();

    private long farmA;
    private long farmB;
    private long turbineA;
    private long turbineB;

    @BeforeEach
    void setUp() {
        truncateAll(jdbc);

        jdbc.update("INSERT INTO turbine_model (model) VALUES ('WinDS3000')");
        long modelId = lastInsertId();

        farmA = insertWindFarm("담당단지");
        farmB = insertWindFarm("비담당단지");
        turbineA = insertTurbine(farmA, modelId, "U1");
        turbineB = insertTurbine(farmB, modelId, "U1"); // farmB 소속(소속 불일치 검증용)
    }

    /**
     * 뒤에 남은 행이 없도록 <b>끝나고도</b> 정리한다.
     * 통합 테스트는 같은 DB 를 공유하므로, 여기서 남긴 assignment/report 가 다른 테스트의
     * {@code users} 삭제를 FK 로 막는다.
     */
    @AfterEach
    void tearDown() {
        truncateAll(jdbc);
    }

    private long lastInsertId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertWindFarm(String name) {
        jdbc.update("INSERT INTO wind_farm (wind_farm_name, wind_farm_latitude, wind_farm_longitude) VALUES (?, ?, ?)",
                name, 34.7, 126.8);
        return lastInsertId();
    }

    private long insertTurbine(long windFarmId, long modelId, String code) {
        jdbc.update("""
                INSERT INTO turbine (wind_farm_id, turbine_model_id, turbine_code, turbine_latitude, turbine_longitude)
                VALUES (?, ?, ?, ?, ?)
                """, windFarmId, modelId, code, 34.7, 126.8);
        return lastInsertId();
    }

    private String baseUrl() {
        return "http://localhost:" + environment.getProperty("local.server.port") + "/api";
    }

    private Long seedUser(String employeeId, Role role) {
        return userJpaRepository
                .save(User.create(employeeId, passwordEncoder.encode("pw12345!"), employeeId, "010-1234-5678", role))
                .getId();
    }

    private void assign(Long userId, long windFarmId) {
        jdbc.update("INSERT INTO assignment (user_id, wind_farm_id, created_at) VALUES (?, ?, NOW(6))",
                userId, windFarmId);
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

    private String loginCookie(String employeeId) {
        ResponseEntity<String> response = send(HttpMethod.POST, "/auth/login",
                "{\"employee_id\":\"" + employeeId + "\",\"password\":\"pw12345!\"}", null);
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("SESSION="))
                .map(c -> c.split(";", 2)[0])
                .findFirst()
                .orElseThrow();
    }

    /** 담당 단지(farmA)를 가진 MANAGER 로 로그인한 쿠키. */
    private String managerCookie() {
        Long userId = seedUser("MGR1", Role.MANAGER);
        assign(userId, farmA);
        return loginCookie("MGR1");
    }

    private String createTurbineReport(String cookie, long windFarmId, long turbineId) {
        ResponseEntity<String> response = send(HttpMethod.POST, "/reports",
                "{\"wind_farm_id\":\"" + windFarmId + "\",\"turbine_id\":\"" + turbineId
                        + "\",\"report_type\":\"TURBINE_OPERATION\"," + PERIOD + "}", cookie);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return response.getBody();
    }

    private static String extractId(String body) {
        int at = body.indexOf("\"id\":\"") + 6;
        return body.substring(at, body.indexOf('"', at));
    }

    @Test
    @DisplayName("생성은 202 로 응답하고 본문에는 식별자만 담긴다")
    void create_returns202WithIdOnly() {
        String cookie = managerCookie();

        String body = createTurbineReport(cookie, farmA, turbineA);

        assertThat(body).contains("\"id\":\"");
        // 본문은 아직 생성되지 않았으므로 응답에 실리지 않는다
        assertThat(body).doesNotContain("\"context\"").doesNotContain("\"title\"");
    }

    @Test
    @DisplayName("담당이 아닌 단지로 생성하면 404(존재 은닉)")
    void create_notAssignedWindFarm_404() {
        String cookie = managerCookie();

        ResponseEntity<String> response = send(HttpMethod.POST, "/reports",
                "{\"wind_farm_id\":\"" + farmB + "\",\"report_type\":\"WIND_FARM_OPERATION\"," + PERIOD + "}", cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("터빈이 다른 단지 소속이면 400 — 복합 FK 가 교차 컬럼 정합성을 강제한다")
    void create_turbineFromAnotherWindFarm_400() {
        String cookie = managerCookie(); // farmA 담당

        // farmA + turbineB(farmB 소속) — 담당 검사는 farmA 로 통과하지만 저장 시 복합 FK 위반
        ResponseEntity<String> response = send(HttpMethod.POST, "/reports",
                "{\"wind_farm_id\":\"" + farmA + "\",\"turbine_id\":\"" + turbineB
                        + "\",\"report_type\":\"TURBINE_OPERATION\"," + PERIOD + "}", cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("R004");
    }

    @Test
    @DisplayName("없는 단지로 ADMIN 이 생성해도 400 — 단일 FK 가 존재를 강제한다")
    void create_missingWindFarm_400() {
        seedUser("ADM1", Role.ADMIN);
        String cookie = loginCookie("ADM1"); // ADMIN 은 담당 검사를 건너뛰므로 저장까지 간다

        ResponseEntity<String> response = send(HttpMethod.POST, "/reports",
                "{\"wind_farm_id\":\"99999999\",\"report_type\":\"WIND_FARM_OPERATION\"," + PERIOD + "}", cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("R004");
    }

    @Test
    @DisplayName("결함·이상 보고서는 공개 생성 API 로 만들 수 없다(400 R002)")
    void create_rejectsNonUserRequestableType() {
        String cookie = managerCookie();

        for (String type : new String[]{"DEFECT_DIAGNOSIS", "ANOMALY_EVENT"}) {
            ResponseEntity<String> response = send(HttpMethod.POST, "/reports",
                    "{\"wind_farm_id\":\"" + farmA + "\",\"turbine_id\":\"" + turbineA
                            + "\",\"report_type\":\"" + type + "\"," + PERIOD + "}", cookie);
            assertThat(response.getStatusCode()).as(type).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).as(type).contains("R002");
        }
    }

    @Test
    @DisplayName("기간이 역전되면 400")
    void create_reversedPeriod_400() {
        String cookie = managerCookie();

        ResponseEntity<String> response = send(HttpMethod.POST, "/reports",
                "{\"wind_farm_id\":\"" + farmA + "\",\"turbine_id\":\"" + turbineA
                        + "\",\"report_type\":\"TURBINE_OPERATION\","
                        + "\"period_start\":\"2026-07-31T23:00:00\",\"period_end\":\"2026-07-01T00:00:00\"}", cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("R003");
    }

    @Test
    @DisplayName("단건 조회는 PENDING 상태와 대상 식별자를 문자열로 돌려준다")
    void getReport_returnsStringIds() {
        String cookie = managerCookie();
        String reportId = extractId(createTurbineReport(cookie, farmA, turbineA));

        ResponseEntity<String> response = send(HttpMethod.GET, "/reports/" + reportId, null, cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"status\":\"PENDING\"")
                .contains("\"report_type\":\"TURBINE_OPERATION\"")
                .contains("\"wind_farm_id\":\"" + farmA + "\"");
    }

    @Test
    @DisplayName("담당이 아닌 사용자에게는 남의 보고서가 404 로 보인다")
    void getReport_outOfScope_404() {
        String ownerCookie = managerCookie();
        String reportId = extractId(createTurbineReport(ownerCookie, farmA, turbineA));

        Long otherId = seedUser("MGR2", Role.MANAGER);
        assign(otherId, farmB); // 다른 단지 담당
        String otherCookie = loginCookie("MGR2");

        assertThat(send(HttpMethod.GET, "/reports/" + reportId, null, otherCookie).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("목록은 담당 단지로 좁혀지고, 유형 필터가 먹으며, 본문은 제외된다")
    void getReports_scopedAndFiltered() {
        String cookie = managerCookie();
        // 담당 단지(farmA) 보고서에 본문을 채운다 — 목록 프로젝션이 본문을 빼는지 검증하려면 본문이 실재해야 한다.
        String reportId = extractId(createTurbineReport(cookie, farmA, turbineA));
        send(HttpMethod.PATCH, "/reports/" + reportId, "{\"context\":\"목록에_안나와야_할_본문A\"}", cookie);
        // 비담당 단지(farmB) 보고서를 직접 심는다 — 범위 격리가 실제로 이를 걸러내는지 검증하려면 존재해야 한다.
        jdbc.update("""
                INSERT INTO report (wind_farm_id, report_type, status, title, context, period_start, period_end)
                VALUES (?, 'WIND_FARM_OPERATION', 'GENERATED', '단지B_전용_보고서', '본문B', '2026-07-01 00:00:00', '2026-07-31 23:00:00')
                """, farmB);

        String body = send(HttpMethod.GET, "/reports", null, cookie).getBody();
        assertThat(body)
                .contains("TURBINE_OPERATION")            // 담당 보고서는 보인다
                .doesNotContain("단지B_전용_보고서")        // 다른 단지 보고서는 범위 밖(격리)
                .doesNotContain("목록에_안나와야_할_본문A"); // 본문은 목록 프로젝션에서 제외
        // 다른 유형으로 필터하면 결과가 비어야 한다
        assertThat(send(HttpMethod.GET, "/reports?report_type=DEFECT_DIAGNOSIS", null, cookie).getBody())
                .contains("\"data\":[]");
    }

    @Test
    @DisplayName("담당 단지가 하나도 없는 사용자에게는 목록이 비어 보인다(전체가 보이면 안 된다)")
    void getReports_noAssignment_seesNothing() {
        String ownerCookie = managerCookie();
        createTurbineReport(ownerCookie, farmA, turbineA);

        seedUser("MGR0", Role.MANAGER); // 담당 배정 없음
        String noAssignmentCookie = loginCookie("MGR0");

        assertThat(send(HttpMethod.GET, "/reports", null, noAssignmentCookie).getBody())
                .contains("\"data\":[]");
    }

    @Test
    @DisplayName("ADMIN 은 담당 배정이 없어도 전체 보고서를 본다")
    void getReports_admin_seesAll() {
        String ownerCookie = managerCookie();
        createTurbineReport(ownerCookie, farmA, turbineA);

        seedUser("ADM1", Role.ADMIN); // 배정 없음 — ADMIN 은 제한을 받지 않는다
        String adminCookie = loginCookie("ADM1");

        assertThat(send(HttpMethod.GET, "/reports", null, adminCookie).getBody())
                .contains("TURBINE_OPERATION");
    }

    @Test
    @DisplayName("생성 직후에도 generated_at 이 채워진다(생성=접수 시각, created_at 방언)")
    void getReport_generatedAtIsSetAtCreation() {
        String cookie = managerCookie();
        String reportId = extractId(createTurbineReport(cookie, farmA, turbineA));

        assertThat(send(HttpMethod.GET, "/reports/" + reportId, null, cookie).getBody())
                .contains("\"generated_at\":\"")   // ISO 문자열이 실려 있다
                .doesNotContain("\"generated_at\":null");
    }

    @Test
    @DisplayName("본문이 상한을 넘으면 500 이 아니라 400 이다")
    void updateReport_tooLongContent_400() {
        String cookie = managerCookie();
        String reportId = extractId(createTurbineReport(cookie, farmA, turbineA));
        String tooLong = "가".repeat(UpdateReportRequest.MAX_CONTEXT_LENGTH + 1);

        ResponseEntity<String> response = send(HttpMethod.PATCH, "/reports/" + reportId,
                "{\"context\":\"" + tooLong + "\"}", cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("본문을 직접 수정할 수 있다(생성 완료 전에도)")
    void updateReport_editsContext() {
        String cookie = managerCookie();
        String reportId = extractId(createTurbineReport(cookie, farmA, turbineA));

        ResponseEntity<String> response = send(HttpMethod.PATCH, "/reports/" + reportId,
                "{\"context\":\"사람이 직접 작성한 본문\"}", cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("사람이 직접 작성한 본문");
    }

    @Test
    @DisplayName("삭제하면 이후 조회가 404 다")
    void deleteReport() {
        String cookie = managerCookie();
        String reportId = extractId(createTurbineReport(cookie, farmA, turbineA));

        assertThat(send(HttpMethod.DELETE, "/reports/" + reportId, null, cookie).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(send(HttpMethod.GET, "/reports/" + reportId, null, cookie).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("보고서를 삭제해도 연결된 점검은 orphan(report_id=null)으로 보존된다")
    void deleteReport_orphansInspections() {
        String cookie = managerCookie();
        long reportId = Long.parseLong(extractId(createTurbineReport(cookie, farmA, turbineA)));
        // 점검은 아직 엔티티가 없어 SQL 로 직접 시드한다(inspection.report_id → report FK).
        jdbc.update("""
                INSERT INTO inspection (turbine_id, user_id, report_id, inspection_start, inspection_end, status, created_at)
                VALUES (?, NULL, ?, NOW(6), NOW(6), 'INSPECTED', NOW(6))
                """, turbineA, reportId);

        assertThat(send(HttpMethod.DELETE, "/reports/" + reportId, null, cookie).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // 원자료(점검)는 남고 report_id 만 NULL 로 끊긴다 — cascade 삭제도 FK 위반(500)도 아니다.
        Integer orphaned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inspection WHERE turbine_id = ? AND report_id IS NULL",
                Integer.class, turbineA);
        assertThat(orphaned).isEqualTo(1);
    }
}
