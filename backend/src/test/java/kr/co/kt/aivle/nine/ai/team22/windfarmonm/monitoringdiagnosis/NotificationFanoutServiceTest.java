package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.infrastructure.UserJpaRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.Report;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.application.NotificationFanoutService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이상 보고서 발생 시 알림 fan-out 을 실제 DB 로 검증한다.
 * 수신자 = ADMIN 전원 + 해당 단지 담당자, ADMIN 이면서 담당자인 사용자는 <b>1건만</b>(dedup).
 */
class NotificationFanoutServiceTest extends IntegrationTestSupport {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 8, 2, 0, 0);

    @Autowired
    NotificationFanoutService fanoutService;
    @Autowired
    ReportRepository reportRepository;
    @Autowired
    UserJpaRepository userJpaRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JdbcTemplate jdbc;

    private long farmId;
    private long adminId;
    private long adminAssignedId;
    private long managerAssignedId;
    private long managerOtherId;
    private long reportId;

    @BeforeEach
    void setUp() {
        truncateAll(jdbc);
        jdbc.update("INSERT INTO wind_farm (wind_farm_name, wind_farm_latitude, wind_farm_longitude) VALUES ('단지', 34.7, 126.8)");
        farmId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        adminId = seedUser("ADM1", Role.ADMIN);
        adminAssignedId = seedUser("ADM2", Role.ADMIN);   // ADMIN 이면서 담당자(dedup 검증)
        managerAssignedId = seedUser("MGR1", Role.MANAGER); // 담당자
        managerOtherId = seedUser("MGR2", Role.MANAGER);    // 비담당(수신 제외)
        assign(adminAssignedId, farmId);
        assign(managerAssignedId, farmId);

        // 이상 보고서 1건(report_id FK 대상). turbine_id 는 null 이라 복합 FK 는 건너뛴다.
        reportId = reportRepository.save(
                Report.request(ReportType.ANOMALY_EVENT, farmId, null, START, END, null, null)).getId();
    }

    private long seedUser(String employeeId, Role role) {
        return userJpaRepository
                .save(User.create(employeeId, passwordEncoder.encode("pw12345!"), employeeId, "010-1234-5678", role))
                .getId();
    }

    private void assign(long userId, long windFarmId) {
        jdbc.update("INSERT INTO assignment (user_id, wind_farm_id, created_at) VALUES (?, ?, NOW(6))", userId, windFarmId);
    }

    @Test
    @DisplayName("ADMIN 전원 + 담당자에게 발송하고, ADMIN·담당자 중복 사용자는 1건만 만든다")
    void notifyAnomalyReport_fansOutDeduped() {
        fanoutService.notifyAnomalyReport(reportId, farmId, "이상 보고서 제목");

        List<Long> recipients = jdbc.queryForList("SELECT user_id FROM notification", Long.class);
        // ADMIN(ADM1, ADM2) ∪ 담당자(MGR1, ADM2) = {ADM1, ADM2, MGR1}. MGR2(비담당)는 제외, ADM2 는 dedup.
        assertThat(recipients).containsExactlyInAnyOrder(adminId, adminAssignedId, managerAssignedId);
        assertThat(recipients).doesNotContain(managerOtherId);
    }

    @Test
    @DisplayName("발송된 알림은 미읽음이고 제목 스냅샷·발생 보고서를 담는다")
    void notifyAnomalyReport_contents() {
        fanoutService.notifyAnomalyReport(reportId, farmId, "이상 보고서 제목");

        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification
                WHERE report_id = ? AND report_title = '이상 보고서 제목' AND is_read = FALSE AND sent_at IS NOT NULL
                """, Integer.class, reportId);
        assertThat(count).isEqualTo(3);
    }
}
