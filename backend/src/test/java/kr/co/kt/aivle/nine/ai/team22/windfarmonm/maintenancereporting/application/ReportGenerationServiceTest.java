package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportGenerationTarget;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.Report;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportStatus;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보고서 생성 파이프라인의 <b>트랜잭션 조각</b>을 실제 DB 로 검증한다: PROCESSING 전이 + 에이전트 호출 대상
 * ({@link ReportGenerationService#markProcessing})과 회신 제목·본문 적재({@link ReportGenerationService#applyGenerated}).
 * event_id 는 대상의 PK(operation=turbine_id, farm_operation=wind_farm_id)이고, 제목은 에이전트가 준다(BE 조립 없음).
 */
class ReportGenerationServiceTest extends IntegrationTestSupport {

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 31, 23, 0);

    @Autowired
    ReportGenerationService generationService;
    @Autowired
    ReportRepository reportRepository;
    @Autowired
    JdbcTemplate jdbc;

    private long farmId;
    private long turbineId;

    @BeforeEach
    void setUp() {
        truncateAll(jdbc);
        jdbc.update("INSERT INTO turbine_model (model) VALUES ('WinDS3000')");
        long modelId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO wind_farm (wind_farm_name, wind_farm_latitude, wind_farm_longitude) VALUES ('한빛풍력', 34.7, 126.8)");
        farmId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("""
                INSERT INTO turbine (wind_farm_id, turbine_model_id, turbine_code, turbine_latitude, turbine_longitude)
                VALUES (?, ?, 'U2', 34.7, 126.8)
                """, farmId, modelId);
        turbineId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long saveReport(ReportType type, Long turbine) {
        return reportRepository.save(
                Report.request(type, farmId, turbine, START, END, null, null)).getId();
    }

    @Test
    @DisplayName("터빈 보고서: PROCESSING 전이 + event_id=turbine_id(PK) + 기간(YYYY-MM-DD) 전달")
    void markProcessing_turbineReport() {
        Long reportId = saveReport(ReportType.TURBINE_OPERATION, turbineId);

        ReportGenerationTarget target = generationService.markProcessing(reportId);

        assertThat(target.agentType()).isEqualTo("operation");
        assertThat(target.eventId()).isEqualTo(turbineId); // 터빈 번호가 아니라 turbine_id(PK)
        assertThat(target.periodStart()).isEqualTo("2026-07-01");
        assertThat(target.periodEnd()).isEqualTo("2026-07-31");
        assertThat(reportRepository.findById(reportId).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.PROCESSING);
    }

    @Test
    @DisplayName("단지 보고서: event_id=wind_farm_id")
    void markProcessing_windFarmReport() {
        Long reportId = saveReport(ReportType.WIND_FARM_OPERATION, null);

        ReportGenerationTarget target = generationService.markProcessing(reportId);

        assertThat(target.agentType()).isEqualTo("farm_operation");
        assertThat(target.eventId()).isEqualTo(farmId);
    }

    @Test
    @DisplayName("완료 적재: 에이전트가 준 제목·본문이 RDS 에 써지고 GENERATED 로 전이한다(BE 가 직접 쓴다)")
    void applyGenerated_writesBodyToRds() {
        Long reportId = saveReport(ReportType.TURBINE_OPERATION, turbineId);
        generationService.markProcessing(reportId);

        generationService.applyGenerated(reportId, "제목X", "## 생성된 본문");

        Report saved = reportRepository.findById(reportId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.GENERATED);
        assertThat(saved.getTitle()).isEqualTo("제목X");
        assertThat(saved.getContext()).isEqualTo("## 생성된 본문");
    }

    @Test
    @DisplayName("경쟁 삭제: 대상 보고서가 없으면 null 을 돌려주고 조용히 끝난다")
    void markProcessing_missingReport_returnsNull() {
        assertThat(generationService.markProcessing(99_999_999L)).isNull();
    }
}
