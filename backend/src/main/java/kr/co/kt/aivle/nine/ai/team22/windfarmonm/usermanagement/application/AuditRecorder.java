package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.RequestContext;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.event.AuditEvent;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.domain.AuditLog;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.domain.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * {@link AuditEvent} 를 audit_log 한 줄로 적재한다.
 * <p>
 * <b>동기·같은 트랜잭션</b>으로 처리한다({@code @Async} 도 {@code @TransactionalEventListener} 도 쓰지 않는다).
 * 커밋 이후에 기록하면 적재가 실패했을 때 업무 변경만 남고 기록은 사라지는데, 접속기록에서는 그 누락이
 * 곧 결함이다. 반대로 같은 트랜잭션이면 적재 실패가 업무 트랜잭션을 되돌린다 — "기록 없이 처리된 개인정보"
 * 를 만들지 않는 쪽을 택했다.
 * <p>
 * 주체를 끝내 특정하지 못하면(비로그인 상태의 행위 등) {@code user_id} 가 NOT NULL 이라 적재할 수 없다.
 * 이때는 경고만 남기고 넘어간다 — 예외로 바꾸면 기록 대상도 아닌 요청이 실패한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRecorder {

    private final AuditLogRepository auditLogRepository;
    private final RequestContext requestContext;

    @EventListener
    public void record(AuditEvent event) {
        Long actorUserId = event.actorUserId() != null ? event.actorUserId() : requestContext.currentUserId();
        if (actorUserId == null) {
            log.warn("감사 로그의 행위 주체를 특정할 수 없어 적재하지 못했다 — action={}, target={}#{}",
                    event.action(), event.targetTable(), event.targetId());
            return;
        }
        auditLogRepository.save(AuditLog.record(
                actorUserId, event.action(), event.targetTable(), event.targetId(),
                requestContext.currentClientIp()));
    }
}
