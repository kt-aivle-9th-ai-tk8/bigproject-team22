package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 31, 23, 0);

    private Report turbineReport() {
        return Report.request(ReportType.TURBINE_OPERATION, 1L, 2L, START, END, null, 10L);
    }

    @Test
    @DisplayName("생성 시 PENDING 이고 본문은 비어 있다")
    void request_startsPendingWithoutContent() {
        Report report = turbineReport();

        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(report.getTitle()).isNull();
        assertThat(report.getContext()).isNull();
        // generatedAt(=접수 시각)은 @CreationTimestamp 라 영속화 시점에 박힌다 — 순수 단위 테스트에선 아직 null
        assertThat(report.getGeneratedAt()).isNull();
        assertThat(report.getCreatedBy()).isEqualTo(10L);
    }

    @Test
    @DisplayName("에이전트 호출 시 PROCESSING 으로 전이한다")
    void markProcessing() {
        Report report = turbineReport();

        report.markProcessing();

        assertThat(report.getStatus()).isEqualTo(ReportStatus.PROCESSING);
    }

    @Test
    @DisplayName("완료 처리하면 본문이 채워지고 GENERATED 가 된다")
    void complete() {
        Report report = turbineReport();
        report.markProcessing();

        report.complete("7월 운영 보고서", "# 본문");

        assertThat(report.getStatus()).isEqualTo(ReportStatus.GENERATED);
        assertThat(report.getTitle()).isEqualTo("7월 운영 보고서");
        assertThat(report.getContext()).isEqualTo("# 본문");
    }

    @Test
    @DisplayName("생성에 실패해 PROCESSING 에 남은 보고서도 본문을 수정할 수 있다")
    void editContext_allowedWhileProcessing() {
        Report report = turbineReport();
        report.markProcessing(); // 에이전트가 끝내 회신하지 않은 상태

        report.editContext("사람이 직접 작성");

        assertThat(report.getContext()).isEqualTo("사람이 직접 작성");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.PROCESSING); // 수정이 상태를 바꾸지는 않는다
    }

    @Test
    @DisplayName("단지 운영 보고서는 대상 터빈이 없다")
    void windFarmReport_hasNoTurbine() {
        Report report = Report.request(ReportType.WIND_FARM_OPERATION, 1L, null, START, END, null, 10L);

        assertThat(report.getTurbineId()).isNull();
        assertThat(ReportType.WIND_FARM_OPERATION.requiresTurbine()).isFalse();
        assertThat(ReportType.TURBINE_OPERATION.requiresTurbine()).isTrue();
        assertThat(ReportType.DEFECT_DIAGNOSIS.requiresTurbine()).isTrue();
        assertThat(ReportType.ANOMALY_EVENT.requiresTurbine()).isTrue();
    }
}
