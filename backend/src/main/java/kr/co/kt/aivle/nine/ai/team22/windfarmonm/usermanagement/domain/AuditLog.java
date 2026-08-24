package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.event.AuditAction;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 감사 로그 한 줄. 개인정보 접속기록(고시 제8조)과 보고서 거버넌스 이력을 함께 담는다.
 * <p>
 * <b>추가만 하고 고치지 않는다.</b> 변경·삭제 메서드를 두지 않는 것이 그 규약의 코드 표현이다 —
 * 기록이 사후에 바뀔 수 있으면 감사 로그로서의 의미가 없다.
 * <p>
 * <b>어느 참조에도 FK 가 없다</b>({@code userId}, {@code targetTable}/{@code targetId} 모두 값 참조).
 * 대상은 물론 행위 주체의 계정이 지워져도 기록은 남아야 하기 때문이다 — 법정 보존기간이 계정 수명보다
 * 길고, FK 가 있으면 오히려 기록이 계정 삭제를 막아 '가입 거절'이 U004 로 실패한다(V17 에서 FK 제거).
 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    /** 행위 주체. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private AuditAction actionType;

    @Column(name = "target_table", length = 50)
    private String targetTable;

    @Column(name = "target_id")
    private Long targetId;

    /** 접속지 정보. 요청 밖(배치 등)에서 발생하면 null 이다. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private AuditLog(Long userId, AuditAction actionType, String targetTable, Long targetId, String ipAddress) {
        this.userId = userId;
        this.actionType = actionType;
        this.targetTable = targetTable;
        this.targetId = targetId;
        this.ipAddress = ipAddress;
    }

    public static AuditLog record(Long userId, AuditAction actionType, String targetTable,
                                  Long targetId, String ipAddress) {
        return new AuditLog(userId, actionType, targetTable, targetId, ipAddress);
    }
}
