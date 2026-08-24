package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.OutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 아웃박스 릴레이 회차 오케스트레이터. 트랜잭션 조각은 {@link OutboxRelayService} 를 <b>외부 호출</b>한다
 * (프록시 적용). ShedLock 으로 다중 인스턴스에서 한 회차를 한 인스턴스만 돈다 — 락이 깨져 중복 발사되어도
 * inferenceId 가 같아 결과 쪽 멱등 가드가 흡수한다(락은 최적화, 멱등이 방어선).
 * 엔드포인트 미설정이면 회차 전체를 건너뛴다(휴면 — CI/로컬에서 무해).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxRelayService relayService;

    @Scheduled(fixedDelayString = "PT10S")
    @SchedulerLock(name = "defect-outbox-relay", lockAtMostFor = "PT5M")
    public void relay() {
        if (!relayService.isDispatchConfigured()) {
            return;
        }
        for (Long eventId : relayService.pendingEventIds()) {
            try {
                relayService.publishOne(eventId); // tx — 접수 + PUBLISHED 전이
            } catch (RuntimeException e) {
                // 이 행은 PENDING 으로 남아 다음 회차에 재시도된다. 나머지 행 처리는 계속한다.
                log.warn("아웃박스 {} 발사 실패 — 다음 회차 재시도", eventId, e);
            }
        }
    }
}
