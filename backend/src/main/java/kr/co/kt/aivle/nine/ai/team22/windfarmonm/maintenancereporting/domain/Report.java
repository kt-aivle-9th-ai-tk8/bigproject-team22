package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain;

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

import java.time.LocalDateTime;

/**
 * 보고서. 본문은 외부 에이전트가 생성하므로 행을 먼저 만들고 나중에 채운다.
 * <p>
 * 다른 애그리거트는 연관관계 매핑 없이 식별자 값으로만 참조한다(assetmanagement 관례와 동일).
 * <p>
 * <b>스키마 소유는 data 담당(hana)</b>이라 테이블은 {@code report}(단수)이고 컬럼도 그쪽 정의를 따른다.
 * 우리 쪽 방언 두 가지를 컬럼 매핑으로 흡수한다:
 * <ul>
 *   <li>{@code approver_id} ← 승인 기작은 없다. '생성 요청자'로 재사용하며 필드는 {@link #createdBy} 로 둔다.</li>
 *   <li>{@code generated_at} ← 별도 접수 시각 컬럼이 없어 이 값을 생성(접수) 시각으로 쓴다({@link #generatedAt}).</li>
 * </ul>
 */
@Entity
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    // @JdbcTypeCode(VARCHAR): Hibernate 가 MySQL 네이티브 ENUM 컬럼을 만들지 않도록 강제(이식성·무마이그레이션 확장).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "report_type", nullable = false, length = 50)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 50)
    private ReportStatus status;

    /** 생성 완료 전까지 null. */
    @Column(name = "title", length = 200)
    private String title;

    /** 보고서 본문 전체(에이전트 생성 마크다운). 생성 완료 전까지 null. */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "context")
    private String context;

    @Column(name = "wind_farm_id", nullable = false)
    private Long windFarmId;

    /** 단지 단위 보고서는 null. */
    @Column(name = "turbine_id")
    private Long turbineId;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    /**
     * 자동 생성을 유발한 이상감지 이벤트. 운영/결함 보고서는 null.
     * <p>
     * 점검(inspection)은 여기서 참조하지 않는다 — 한 보고서가 여러 점검을 아우르므로
     * 링크는 반대 방향({@code inspection.report_id})으로 건다.
     */
    @Column(name = "anomaly_event_id")
    private Long anomalyEventId;

    /**
     * 생성 요청자. 이상감지처럼 사람이 요청하지 않은 경우 null.
     * <p>
     * DB 컬럼은 {@code approver_id} 다 — 승인 기작을 두지 않기로 하면서 그 컬럼을 생성 요청자로 재사용한다.
     * 컬럼 개명은 이후 과제이고, 그때까지 이 매핑이 방언을 흡수한다.
     */
    @Column(name = "approver_id")
    private Long createdBy;

    /**
     * 보고서 생성(접수) 시각.
     * <p>
     * 스키마에 별도 {@code created_at} 이 없어 {@code generated_at} 컬럼을 접수 시각으로 쓴다 — 우리 내부
     * 방언으로는 created_at 에 해당한다. {@link CreationTimestamp} 로 INSERT 시점에 박히고 이후 불변이라,
     * 목록 최신순 정렬의 근거가 된다("본문이 채워진 시각"이 아니다 — 준비 여부는 {@link #status} 로 판단한다).
     */
    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private LocalDateTime generatedAt;

    private Report(ReportType reportType, ReportStatus status, Long windFarmId, Long turbineId,
                   LocalDateTime periodStart, LocalDateTime periodEnd, Long anomalyEventId, Long createdBy) {
        this.reportType = reportType;
        this.status = status;
        this.windFarmId = windFarmId;
        this.turbineId = turbineId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.anomalyEventId = anomalyEventId;
        this.createdBy = createdBy;
    }

    /** 사용자 요청으로 생성한다. 곧바로 에이전트에 넘길 대상이므로 PENDING 으로 시작한다. */
    public static Report request(ReportType reportType, Long windFarmId, Long turbineId,
                                 LocalDateTime periodStart, LocalDateTime periodEnd,
                                 Long anomalyEventId, Long createdBy) {
        return new Report(reportType, ReportStatus.PENDING, windFarmId, turbineId,
                periodStart, periodEnd, anomalyEventId, createdBy);
    }

    /** 에이전트에 생성을 요청했음을 기록한다. */
    public void markProcessing() {
        this.status = ReportStatus.PROCESSING;
    }

    /** 에이전트가 회신한 본문을 적재하고 완료 처리한다. */
    public void complete(String title, String context) {
        this.title = title;
        this.context = context;
        this.status = ReportStatus.GENERATED;
    }

    /**
     * 사용자가 본문을 직접 수정한다.
     * <p>
     * 상태를 검사하지 않는다 — 생성에 실패해 PROCESSING 에 남은 보고서도 손댈 수 있어야 하기 때문이다
     * ({@link ReportStatus} 참고).
     */
    public void editContext(String context) {
        this.context = context;
    }
}
