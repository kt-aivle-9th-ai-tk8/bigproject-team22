package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.AnomalyEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.AnomalyEventRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.AnomalyEventType;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.AnomalyScope;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.AnomalyTier;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이상 이벤트 스키마 계약을 실제 DB 로 확인한다.
 * <p>
 * 핵심은 <b>멱등키가 DB 제약으로 실제 강제되는지</b>다. 매시각 배치는 진행 중인 이벤트를 같은 키로 다시
 * 산출하는데, 이 제약이 없으면 회차마다 행이 쌓인다. 분산 락은 장애 전환 중 중복 획득이 가능해
 * 상호배제를 보장하지 않으므로, 중복을 막는 실제 근거는 이 제약이다.
 */
class AnomalyEventSchemaIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 1, 0, 0);

    @Autowired
    AnomalyEventRepository anomalyEventRepository;
    @Autowired
    JdbcTemplate jdbc;

    private long turbineA;
    private long turbineB;

    @BeforeEach
    void setUp() {
        truncateAll(jdbc);
        jdbc.update("INSERT INTO turbine_models (model) VALUES ('WinDS3000')");
        long modelId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO wind_farms (wind_farm_name, wind_farm_latitude, wind_farm_longitude) VALUES ('단지', 34.7, 126.8)");
        long farmId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        turbineA = insertTurbine(farmId, modelId, "U1");
        turbineB = insertTurbine(farmId, modelId, "U2");
    }

    private long insertTurbine(long farmId, long modelId, String code) {
        jdbc.update("""
                INSERT INTO turbines (wind_farm_id, turbine_model_id, turbine_code, turbine_latitude, turbine_longitude)
                VALUES (?, ?, ?, ?, ?)
                """, farmId, modelId, code, 34.7, 126.8);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private AnomalyEvent stop(long turbineId, LocalDateTime startTime) {
        return AnomalyEvent.detected(turbineId, AnomalyTier.A, AnomalyEventType.PROLONGED_STOP, startTime, null, null);
    }

    @Test
    @DisplayName("같은 멱등키로 두 번 저장하면 DB 가 거부한다")
    void duplicateIdentity_isRejected() {
        anomalyEventRepository.save(stop(turbineA, START));

        assertThatThrownBy(() -> anomalyEventRepository.save(stop(turbineA, START)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("호기·계층·유형·시작시각 중 하나만 달라도 별개 이벤트다")
    void differentIdentity_isAllowed() {
        anomalyEventRepository.save(stop(turbineA, START));

        anomalyEventRepository.save(stop(turbineB, START));                       // 호기 다름
        anomalyEventRepository.save(stop(turbineA, START.plusHours(1)));          // 시작시각 다름
        anomalyEventRepository.save(AnomalyEvent.detected(                        // 유형 다름
                turbineA, AnomalyTier.A, AnomalyEventType.DATA_MISSING, START, null, AnomalyScope.FARM));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM anomaly_events", Integer.class)).isEqualTo(4);
    }

    @Test
    @DisplayName("멱등키로 찾아 갱신하면 행이 늘지 않는다(매시각 재산출 시나리오)")
    void refreshByIdentity_doesNotAddRow() {
        anomalyEventRepository.save(stop(turbineA, START));

        AnomalyEvent found = anomalyEventRepository
                .findByIdentity(turbineA, AnomalyTier.A, AnomalyEventType.PROLONGED_STOP, START)
                .orElseThrow();
        found.refresh(null, null, 100.0, 0.0, null, -100.0, null, 900.0);
        anomalyEventRepository.save(found);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM anomaly_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estimated_loss_kwh FROM anomaly_events", Double.class)).isEqualTo(900.0);
    }

    @Test
    @DisplayName("진행 중인 이벤트만 골라낸다")
    void findOngoing() {
        anomalyEventRepository.save(stop(turbineA, START));                       // 진행 중
        AnomalyEvent closed = stop(turbineB, START);
        closed.refresh(START.plusHours(2), null, null, null, null, null, null, null);
        anomalyEventRepository.save(closed);

        assertThat(anomalyEventRepository.findOngoing())
                .extracting(AnomalyEvent::getTurbineId)
                .containsExactly(turbineA);
    }

    @Test
    @DisplayName("관측 없는 유형은 수치 컬럼이 전부 NULL 이어도 저장된다")
    void dataMissing_persistsWithNullMetrics() {
        anomalyEventRepository.save(AnomalyEvent.detected(
                turbineA, AnomalyTier.A, AnomalyEventType.DATA_MISSING, START, null, AnomalyScope.FARM));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM anomaly_events WHERE z_score IS NULL AND estimated_loss_kwh IS NULL",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("scada_record 에 추론 산출 컬럼이 준비되어 있다")
    void scadaRecordHasInferenceColumns() {
        // 적재 주체(P7)가 들어오기 전이라 엔티티에는 매핑하지 않았다. 스키마만 선반영했음을 고정한다.
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'scada_record'
                  AND column_name IN ('wind_speed','air_density','norm_wind_speed','is_stopped',
                                      'train_mask','expected_power_pooled','expected_power_unit')
                """, Integer.class);

        assertThat(count).isEqualTo(7);
    }
}
