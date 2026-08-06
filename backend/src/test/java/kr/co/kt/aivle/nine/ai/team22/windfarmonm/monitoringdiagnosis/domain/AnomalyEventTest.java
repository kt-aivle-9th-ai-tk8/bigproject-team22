package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyEventTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 1, 0, 0);

    private AnomalyEvent ongoingStop() {
        return AnomalyEvent.detected(1L, AnomalyTier.A, AnomalyEventType.PROLONGED_STOP, START, null, null);
    }

    @Test
    @DisplayName("종료 시각이 없으면 진행 중이다")
    void isOngoing() {
        assertThat(ongoingStop().isOngoing()).isTrue();
        assertThat(AnomalyEvent.detected(1L, AnomalyTier.A, AnomalyEventType.PROLONGED_STOP,
                START, START.plusHours(3), null).isOngoing()).isFalse();
    }

    @Test
    @DisplayName("진행 중이면 기준 시각까지로 지속 시간을 계산한다")
    void duration_ongoing_measuredUntilGivenInstant() {
        AnomalyEvent event = ongoingStop();

        assertThat(event.duration(START.plusHours(30))).isEqualTo(Duration.ofHours(30));
    }

    @Test
    @DisplayName("종료된 이벤트는 기준 시각과 무관하게 실제 구간으로 계산한다")
    void duration_closed_ignoresGivenInstant() {
        AnomalyEvent event = AnomalyEvent.detected(1L, AnomalyTier.A, AnomalyEventType.PROLONGED_STOP,
                START, START.plusHours(5), null);

        assertThat(event.duration(START.plusDays(10))).isEqualTo(Duration.ofHours(5));
    }

    @Test
    @DisplayName("보고서 자동생성 게이트(24시간) 경계에서 판정이 갈린다")
    void duration_gateBoundary() {
        AnomalyEvent event = ongoingStop();
        Duration gate = Duration.ofHours(24);

        // 23:59:59 → 미달, 24:00:00 → 도달. 경계에서 헷갈리면 하루치 보고서가 통째로 어긋난다.
        assertThat(event.duration(START.plusHours(24).minusSeconds(1)).compareTo(gate)).isNegative();
        assertThat(event.duration(START.plusHours(24)).compareTo(gate)).isZero();
    }

    @Test
    @DisplayName("갱신하면 종료 시각과 지표가 덮이고 멱등키는 그대로다")
    void refresh_updatesMetricsKeepingIdentity() {
        AnomalyEvent event = ongoingStop();

        event.refresh(START.plusHours(6), AnomalyScope.TURBINE, 100.0, 0.0, -3.2, -100.0, null, 600.0);

        assertThat(event.getEndTime()).isEqualTo(START.plusHours(6));
        assertThat(event.getScope()).isEqualTo(AnomalyScope.TURBINE);
        assertThat(event.getEstimatedLossKwh()).isEqualTo(600.0);
        // 멱등키를 이루는 값은 건드리지 않는다
        assertThat(event.getTurbineId()).isEqualTo(1L);
        assertThat(event.getTier()).isEqualTo(AnomalyTier.A);
        assertThat(event.getEventType()).isEqualTo(AnomalyEventType.PROLONGED_STOP);
        assertThat(event.getStartTime()).isEqualTo(START);
    }

    @Test
    @DisplayName("관측이 없는 유형은 수치 지표가 비어 있어도 성립한다")
    void dataMissing_allowsEmptyMetrics() {
        AnomalyEvent event = AnomalyEvent.detected(1L, AnomalyTier.A, AnomalyEventType.DATA_MISSING,
                START, null, AnomalyScope.FARM);

        assertThat(event.getExpectedPower()).isNull();
        assertThat(event.getActualPower()).isNull();
        assertThat(event.getZScore()).isNull();
        assertThat(event.getEstimatedLossKwh()).isNull();
        assertThat(event.getScope()).isEqualTo(AnomalyScope.FARM);
    }
}
