package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 점검(터빈 1대당 1행). 드론 촬영 이미지의 업로드~추론~결함 적재까지의 진행 상태를 들고 있다.
 * 대상 터빈·수행자·연계 보고서는 id 값으로만 참조한다(BC 간 @ManyToOne 금지).
 */
@Entity
@Table(name = "inspection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id")
    private Long id;

    @Column(name = "turbine_id", nullable = false)
    private Long turbineId;

    /** 점검 수행자(생성 요청자). */
    @Column(name = "user_id")
    private Long userId;

    /** 함께 생성된 결함 진단 보고서. 보고서가 삭제되면 링크만 끊긴다(V8: ON DELETE SET NULL). */
    @Column(name = "report_id")
    private Long reportId;

    /** 드론 촬영 시작(V5 주석: 촬영 날짜). */
    @Column(name = "inspection_start", nullable = false)
    private LocalDateTime inspectionStart;

    @Column(name = "inspection_end", nullable = false)
    private LocalDateTime inspectionEnd;

    // @JdbcTypeCode(VARCHAR): Hibernate 가 MySQL 네이티브 ENUM 컬럼을 만들지 않도록 강제(관례).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 50)
    private InspectionStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private Inspection(Long turbineId, Long userId, Long reportId,
                       LocalDateTime inspectionStart, LocalDateTime inspectionEnd) {
        this.turbineId = turbineId;
        this.userId = userId;
        this.reportId = reportId;
        this.inspectionStart = inspectionStart;
        this.inspectionEnd = inspectionEnd;
        this.status = InspectionStatus.UPLOADING;
    }

    /** 점검을 등록한다. 업로드가 이어질 것이므로 UPLOADING 으로 시작한다. */
    public static Inspection request(Long turbineId, Long userId, Long reportId,
                                     LocalDateTime inspectionStart, LocalDateTime inspectionEnd) {
        return new Inspection(turbineId, userId, reportId, inspectionStart, inspectionEnd);
    }

    /**
     * 업로드 완료 → 추론 진행으로 전이한다. UPLOADING 에서만 허용된다 —
     * 중복 완료 통보(INSPECTING)나 종료된 점검(INSPECTED)이면 {@link ErrorCode#INSPECTION_STATE_CONFLICT}(400 — 명세).
     */
    public void markInspecting() {
        if (status != InspectionStatus.UPLOADING) {
            throw new BusinessException(ErrorCode.INSPECTION_STATE_CONFLICT);
        }
        this.status = InspectionStatus.INSPECTING;
    }

    /** 결함 적재 완료(P5 가 호출). INSPECTING 에서만 허용된다. */
    public void markInspected() {
        if (status != InspectionStatus.INSPECTING) {
            throw new BusinessException(ErrorCode.INSPECTION_STATE_CONFLICT);
        }
        this.status = InspectionStatus.INSPECTED;
    }
}
