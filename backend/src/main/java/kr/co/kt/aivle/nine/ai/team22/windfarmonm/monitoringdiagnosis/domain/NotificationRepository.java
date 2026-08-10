package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain;

import java.util.List;
import java.util.Optional;

/**
 * 알림 저장소 포트.
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    /** fan-out: 수신자별 알림을 한 번에 저장한다. */
    void saveAll(List<Notification> notifications);

    Optional<Notification> findById(Long notificationId);

    void delete(Notification notification);

    /** 사용자의 알림을 최신순으로. */
    List<Notification> findByUserIdOrderBySentAtDesc(Long userId);

    /** 사용자의 <b>안 읽은</b> 알림을 최신순으로. */
    List<Notification> findByUserIdAndReadIsFalseOrderBySentAtDesc(Long userId);
}
