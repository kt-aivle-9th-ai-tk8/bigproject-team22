package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain;

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
 * 트랜잭셔널 아웃박스 행. 도메인 변경(예: 업로드 완료)과 "추론을 요청해야 한다"는 사실을
 * <b>한 트랜잭션</b>에 기록하고, 실제 발사(SageMaker Async 호출)는 별도 릴레이(폴러)가 수행한다 —
 * 커밋 직후 앱이 죽어도 요청이 유실되지 않는다.
 * <p>
 * 테이블(V6)은 범용 스키마지만 현재 소비자는 defectinspection 뿐이라 이 BC 가 소유한다.
 * 다른 BC 가 쓰게 되면 그때 공용 위치로 옮긴다.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    /** 대상 엔티티 id. 어떤 애그리거트든 담도록 컬럼이 VARCHAR 다(V6). */
    @Column(name = "aggregate_id", nullable = false, length = 50)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** 릴레이가 발행할 실제 데이터 본문(JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    /** 발행 대기 행을 만든다. */
    public static OutboxEvent pending(String aggregateType, String aggregateId, String eventType, String payload) {
        return new OutboxEvent(aggregateType, aggregateId, eventType, payload);
    }

    /** 릴레이가 발행(추론 접수)을 마쳤다. */
    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
    }

    /** 결과 통보를 받아 처리(결함 적재)까지 끝냈다. */
    public void markCompleted() {
        this.status = OutboxStatus.COMPLETED;
    }

    /** 발행 불가/추론 실패. */
    public void markFailed() {
        this.status = OutboxStatus.FAILED;
    }

    /** 이미 결과 처리가 끝난 행인지(SQS at-least-once 중복 통보 멱등 처리용). */
    public boolean isCompleted() {
        return status == OutboxStatus.COMPLETED;
    }
}
