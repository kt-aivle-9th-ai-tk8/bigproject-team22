package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.Notification;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 읽음/삭제. 열람 판정은 {@link NotificationQueryService#readOwned}(타인 알림은 404)를 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final NotificationQueryService notificationQueryService;

    /** 읽음 처리(멱등 — 이미 읽음이어도 안전). */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationQueryService.readOwned(userId, notificationId);
        notification.markRead(); // 관리 엔티티 — dirty checking 으로 flush
    }

    @Transactional
    public void delete(Long userId, Long notificationId) {
        Notification notification = notificationQueryService.readOwned(userId, notificationId);
        notificationRepository.delete(notification);
    }
}
