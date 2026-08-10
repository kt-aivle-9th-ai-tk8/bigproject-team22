package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 사용자 알림. 현재는 <b>이상 보고서 자동 생성</b>이 유일한 발생 원인이다("이상 보고서 발생 기록 = 알림").
 * 수신자당 1행 생성된다(fan-out).
 * <p>
 * 다른 애그리거트는 연관관계 매핑 없이 식별자 값으로만 참조한다(관례). {@code reportTitle} 은 발송 당시
 * 제목의 <b>스냅샷</b>이라, 원본 보고서 제목이 바뀌거나 보고서가 삭제돼도(그때 {@code reportId} 는 NULL)
 * 알림 문구는 보존된다.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    /** 알림 수신자. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 알림 발생 원인 보고서. 보고서가 삭제되면 NULL 이 된다(ON DELETE SET NULL). */
    @Column(name = "report_id")
    private Long reportId;

    /** 발송 당시 보고서 제목 스냅샷. */
    @Column(name = "report_title", length = 200)
    private String reportTitle;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    private Notification(Long userId, Long reportId, String reportTitle) {
        this.userId = userId;
        this.reportId = reportId;
        this.reportTitle = reportTitle;
        this.read = false;
    }

    /** 특정 수신자에게 보낼 알림을 만든다(미읽음 상태로 시작). */
    public static Notification of(Long userId, Long reportId, String reportTitle) {
        return new Notification(userId, reportId, reportTitle);
    }

    /** 읽음 처리. 이미 읽음이어도 안전하다(멱등). */
    public void markRead() {
        this.read = true;
    }
}
