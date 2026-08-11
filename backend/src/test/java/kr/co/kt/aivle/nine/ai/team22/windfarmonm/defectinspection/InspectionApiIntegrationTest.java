package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionStoragePort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.PartSide;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 점검(결함탐지) API 계약을 실제 HTTP·DB 로 검증한다. 저장소 포트만 목으로 대체한다 —
 * presign 은 AWS 자격증명이 필요해 CI 에서 실행 불가하고, 그 경계 바깥(인가·행 생성·아웃박스)이 이 테스트의 대상이다.
 */
class InspectionApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    UserJpaRepository userJpaRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    Environment environment;
    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    InspectionStoragePort storagePort;

    private final RestClient client = RestClient.create();

    private long farmId;
    private long turbineId;
    private long bladeA;

    @BeforeEach
    void setUp() {
        truncateAll(jdbc);
        jdbc.update("INSERT INTO turbine_model (model) VALUES ('U93')");
        long modelId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO wind_farm (wind_farm_name, wind_farm_latitude, wind_farm_longitude) VALUES ('화순', 35.1, 127.0)");
        farmId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("""
                INSERT INTO turbine (wind_farm_id, turbine_model_id, turbine_code, turbine_latitude, turbine_longitude)
                VALUES (?, ?, 'U1', 35.1, 127.0)
                """, farmId, modelId);
        turbineId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        for (String tag : List.of("A", "B", "C")) {
            jdbc.update("INSERT INTO blade (turbine_id, blade_tag) VALUES (?, ?)", turbineId, tag);
        }
        bladeA = jdbc.queryForObject("SELECT blade_id FROM blade WHERE blade_tag='A' AND turbine_id=?", Long.class, turbineId);

        long managerId = seedUser("MGR1");
        seedUser("MGR2"); // 비담당(은닉 검증용)
        jdbc.update("INSERT INTO assignment (user_id, wind_farm_id, created_at) VALUES (?, ?, NOW(6))", managerId, farmId);

        when(storagePort.presignImageUpload(anyLong(), anyLong(), any(), anyInt()))
                .thenAnswer(inv -> new InspectionStoragePort.UploadTarget(
                        "content/inspections/%d/%d/%s/%d.jpg".formatted(
                                (long) inv.getArgument(0), (long) inv.getArgument(1),
                                inv.getArgument(2), (int) inv.getArgument(3)),
                        "https://s3.presigned.example/upload"));
    }

    @AfterEach
    void tearDown() {
        truncateAll(jdbc); // 잔여 행(assignment 등)이 다른 테스트 클래스의 정리(user 삭제)를 막지 않도록
    }

    private long seedUser(String employeeId) {
        return userJpaRepository
                .save(User.create(employeeId, passwordEncoder.encode("pw12345!"), employeeId, "010-1234-5678", Role.MANAGER))
                .getId();
    }

    private String baseUrl() {
        return "http://localhost:" + environment.getProperty("local.server.port") + "/api";
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

    private ResponseEntity<String> post(String path, String jsonBody, String cookie) {
        RestClient.RequestBodySpec spec = client.method(HttpMethod.POST).uri(baseUrl() + path)
                .header(HttpHeaders.COOKIE, cookie);
        if (jsonBody != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(jsonBody);
        }
        return spec.exchange((request, response) -> ResponseEntity
                .status(response.getStatusCode())
                .body(response.bodyTo(String.class)));
    }

    private String createBody() {
        return """
                {"wind_farm_id":"%d",
                 "inspection_start":"2026-08-01T00:00:00","inspection_end":"2026-08-02T00:00:00",
                 "turbines":[{"turbine_id":"%d",
                   "blades":[{"blade_id":"%d","leading_edge_count":2,"pressure_side_count":1,
                              "suction_side_count":0,"trailing_edge_count":0}]}],
                 "context":"블레이드 A 전연 위주로 촬영"}
                """.formatted(farmId, turbineId, bladeA);
    }

    @Test
    @DisplayName("생성: 200 + 터빈별 inspection_id·부위별 URL 목록·세션 report_id, 점검(UPLOADING)·보고서(PENDING+참고사항) 행")
    void createInspection() {
        String cookie = loginCookie("MGR1");

        ResponseEntity<String> response = post("/inspections", createBody(), cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"wind_farm_id\":\"").contains("\"inspection_id\":\"").contains("\"report_id\":\"")
                .contains("\"leading_edge_upload_urls\":[\"https://s3.presigned.example/upload\",\"https://s3.presigned.example/upload\"]")
                .contains("\"pressure_side_upload_urls\":[\"https://s3.presigned.example/upload\"]")
                .contains("\"suction_side_upload_urls\":[]"); // count 0 → 빈 목록

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM inspection WHERE status='UPLOADING' AND turbine_id=?
                  AND inspection_start='2026-08-01 00:00:00' AND inspection_end='2026-08-02 00:00:00'
                """, Long.class, turbineId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM report WHERE report_type='DEFECT_DIAGNOSIS' AND status='PENDING'
                  AND wind_farm_id=? AND turbine_id IS NULL AND context='블레이드 A 전연 위주로 촬영'
                """, Long.class, farmId)).isEqualTo(1);
        // 점검이 세션 보고서를 가리킨다
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM inspection i JOIN report r ON i.report_id = r.report_id
                WHERE r.report_type='DEFECT_DIAGNOSIS'
                """, Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("생성: 비담당 사용자는 404(존재 은닉), 타 단지 터빈은 404, 그 터빈에 없는 블레이드는 400 D003")
    void createInspection_authAndTarget() {
        assertThat(post("/inspections", createBody(), loginCookie("MGR2")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        String cookie = loginCookie("MGR1");
        String wrongBlade = createBody().replace("\"blade_id\":\"%d\"".formatted(bladeA), "\"blade_id\":\"999999\"");
        ResponseEntity<String> response = post("/inspections", wrongBlade, cookie);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("D003");
    }

    @Test
    @DisplayName("업로드 완료: 200(data null) + 상태 INSPECTING + 이미지당 아웃박스 행, 중복 통보는 400(D002)")
    void imagesUploaded() {
        String cookie = loginCookie("MGR1");
        post("/inspections", createBody(), cookie);
        long inspectionId = jdbc.queryForObject("SELECT inspection_id FROM inspection", Long.class);

        when(storagePort.listUploadedImages(inspectionId)).thenReturn(List.of(
                new InspectionStoragePort.UploadedImage(
                        "content/inspections/%d/%d/LE/1.jpg".formatted(inspectionId, bladeA), bladeA, PartSide.LE),
                new InspectionStoragePort.UploadedImage(
                        "content/inspections/%d/%d/LE/2.jpg".formatted(inspectionId, bladeA), bladeA, PartSide.LE)));

        ResponseEntity<String> response = post("/inspections/" + inspectionId + "/images-uploaded", null, cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"data\":null");
        assertThat(jdbc.queryForObject("SELECT status FROM inspection WHERE inspection_id=?", String.class, inspectionId))
                .isEqualTo("INSPECTING");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE status='PENDING' AND event_type='InspectionImageUploaded' AND aggregate_id=?
                """, Long.class, String.valueOf(inspectionId))).isEqualTo(2);
        String payload = jdbc.queryForObject(
                "SELECT payload FROM outbox_event ORDER BY id LIMIT 1", String.class);
        assertThat(payload).contains("\"inspection_id\"").contains("\"image_key\"")
                .contains("\"blade_id\"").contains("\"part_side\"");

        // 중복 통보는 400 (명세: inspection already upload completed)
        assertThat(post("/inspections/" + inspectionId + "/images-uploaded", null, cookie).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("업로드 완료: 타인(비담당) 점검은 404(존재 은닉)")
    void imagesUploaded_hidden() {
        String cookie = loginCookie("MGR1");
        post("/inspections", createBody(), cookie);
        long inspectionId = jdbc.queryForObject("SELECT inspection_id FROM inspection", Long.class);

        assertThat(post("/inspections/" + inspectionId + "/images-uploaded", null, loginCookie("MGR2")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
