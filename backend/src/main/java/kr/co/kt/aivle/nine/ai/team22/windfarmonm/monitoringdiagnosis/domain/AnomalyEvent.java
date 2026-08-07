package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 이상 이벤트. 매시각 배치가 추론 결과를 받아 적재한다.
 * <p>
 * 수치 지표가 대부분 nullable 인 이유는 유형·계층마다 산출되는 값이 다르기 때문이다.
 * {@link AnomalyEventType#DATA_MISSING} 은 관측 자체가 없어 출력·손실량을 계산할 수 없고,
 * 계층 A 는 z-score·30일 에너지비가, 계층 B 는 일부 순시값이 비어 있다.
 * <p>
 * 지속 시간은 컬럼으로 두지 않고 {@link #duration()} 로 유도한다 — 진행 중인 이벤트는 매시각 값이
 * 달라지므로, 저장해 두면 갱신하지 않는 한 낡은 값이 남는다.
 */
@Entity
@Table(name = "anomaly_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnomalyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    /** 단지 단위 사안({@link AnomalyScope#FARM})도 호기별로 한 행씩 생긴다. */
    @Column(name = "turbine_id", nullable = false)
    private Long turbineId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /** null 이면 아직 진행 중이다. */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "expected_power")
    private Double expectedPower;

    @Column(name = "actual_power")
    private Double actualPower;

    /** 계층 A 는 산출하지 않는다. */
    @Column(name = "z_score")
    private Double zScore;

    @Column(name = "deviation_pct")
    private Double deviationPct;

    // @JdbcTypeCode(VARCHAR): Hibernate 가 MySQL 네이티브 ENUM 컬럼을 만들지 않도록 강제(이식성·무마이그레이션 확장).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tier", nullable = false, length = 20)
    private AnomalyTier tier;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 50)
    private AnomalyEventType eventType;

    /** {@link AnomalyEventType#DATA_MISSING} 에서만 채워진다. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "scope", length = 20)
    private AnomalyScope scope;

    /** 계층 B 지표. 계층 A 는 null. */
    @Column(name = "energy_ratio_30d")
    private Double energyRatio30d;

    /** {@link AnomalyEventType#DATA_MISSING} 은 관측이 없어 산출 불가(null). */
    @Column(name = "estimated_loss_kwh")
    private Double estimatedLossKwh;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private AnomalyEvent(Long turbineId, AnomalyTier tier, AnomalyEventType eventType,
                         LocalDateTime startTime, LocalDateTime endTime, AnomalyScope scope) {
        // 멱등키를 이루는 네 필드가 null 이면 DB UNIQUE 제약이 무의미해지고, save 시점의 제약 위반으로만
        // 뒤늦게 드러난다. 생성 시점에 거부해 "언제 잘못됐는지"를 명확히 한다.
        if (turbineId == null || tier == null || eventType == null || startTime == null) {
            throw new IllegalArgumentException(
                    "멱등키 필드(turbineId/tier/eventType/startTime)는 null 일 수 없다");
        }
        this.turbineId = turbineId;
        this.tier = tier;
        this.eventType = eventType;
        this.startTime = startTime;
        this.endTime = endTime == null ? null : requireAfterStart(endTime); // 종료 시각이 있으면 시작 이후여야 한다
        this.scope = scope;
    }

    /**
     * 이벤트를 새로 만든다. 인자 넷이 멱등키를 이룬다 — 같은 조합이 다시 산출되면 새로 만들지 말고
     * {@link #refresh} 로 갱신해야 한다.
     */
    public static AnomalyEvent detected(Long turbineId, AnomalyTier tier, AnomalyEventType eventType,
                                        LocalDateTime startTime, LocalDateTime endTime, AnomalyScope scope) {
        return new AnomalyEvent(turbineId, tier, eventType, startTime, endTime, scope);
    }

    /**
     * 진행 중인 이벤트의 스냅샷을 <b>전량 갱신</b>한다.
     * <p>
     * 매시각 배치가 같은 이벤트를 다시 산출하면 멱등키로 찾아 이 메서드로 덮는다. 전달한 값으로 모든 지표를
     * 덮으므로 <b>호출자는 항상 완전한 스냅샷을 넘겨야 한다</b> — 일부만 주면 나머지가 null 로 지워진다.
     * 종료 시각만 찍으려면 이 메서드가 아니라 {@link #close} 를 쓸 것(지표를 실수로 지우지 않도록 분리했다).
     */
    public void refresh(LocalDateTime endTime, AnomalyScope scope,
                        Double expectedPower, Double actualPower, Double zScore,
                        Double deviationPct, Double energyRatio30d, Double estimatedLossKwh) {
        this.endTime = endTime == null ? null : requireAfterStart(endTime); // null = 진행 중 유지
        this.scope = scope;
        this.expectedPower = expectedPower;
        this.actualPower = actualPower;
        this.zScore = zScore;
        this.deviationPct = deviationPct;
        this.energyRatio30d = energyRatio30d;
        this.estimatedLossKwh = estimatedLossKwh;
    }

    /**
     * 진행 중이던 이벤트를 종료 처리한다. 종료 시각만 찍고 지표는 마지막 갱신값 그대로 둔다.
     * <p>
     * 배치가 "이전 회차엔 있었는데 이번엔 사라진" 이벤트를 끝낼 때 쓴다 — 마지막 상태는 이미 직전 {@link #refresh}
     * 로 저장돼 있으므로, 종료가 그 값을 지우면 안 된다.
     * <p>
     * 종료 시각은 필수이며 시작 이후여야 한다. null 이면 {@link #isOngoing()} 이 다시 true 가 되어 "종료"의
     * 의미가 뒤집히고, 시작보다 이르면 {@link #duration}이 음수가 되어 24시간 게이트를 조용히 무력화한다.
     */
    public void close(LocalDateTime endTime) {
        if (endTime == null) {
            throw new IllegalArgumentException("종료 시각은 필수다");
        }
        this.endTime = requireAfterStart(endTime);
    }

    /** 종료 시각이 시작 이후인지 보장한다. 위반은 배치 상류(추론 결과)나 시계 스큐의 오류다. */
    private LocalDateTime requireAfterStart(LocalDateTime endTime) {
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException(
                    "종료 시각이 시작보다 이르다: start=" + startTime + ", end=" + endTime);
        }
        return endTime;
    }

    /** 아직 끝나지 않은 이벤트인지. */
    public boolean isOngoing() {
        return endTime == null;
    }

    /**
     * 지속 시간. 진행 중이면 기준 시각까지로 계산한다.
     * <p>
     * 보고서 자동생성 게이트(예: 정지 24시간 이상)가 이 값을 근거로 삼는다. 기준 시각을 인자로 받는 이유는
     * 배치가 다루는 "회차 기준 시각"과 실제 호출 시각이 다를 수 있고, 그래야 테스트가 결정적이기 때문이다.
     * <p>
     * 음수가 되지 않도록 여기서 따로 검증하지 않는다. 종료 시각은 저장 시점에 시작 이후로 강제되고
     * ({@link #close}/{@link #refresh}), 진행 중이면 기준 시각은 항상 현재(=시작 이후)이기 때문이다.
     */
    public Duration duration(LocalDateTime at) {
        return Duration.between(startTime, endTime != null ? endTime : at);
    }
}
