package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.application.dto.NotificationResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.Notification;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.NotificationRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림 조회. 사용자는 <b>자기 알림만</b> 볼 수 있고, 타인 알림은 존재하지 않는 것과 동일하게
 * {@link ErrorCode#NOTIFICATION_NOT_FOUND} 로 응답한다(존재 은닉).
 */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    /**
     * 내 알림 목록(최신순). {@code unreadOnly} 면 안 읽은 것만. 페이징은 두지 않는다(MVP).
     * <p>
     * TODO: 현재 FE 는 전체 목록에 {@code is_read} 를 함께 담아 받아 표시하며, <b>안 읽은 것만 조회하는 경로는
     *   실제로 쓰지 않는다.</b> {@code unreadOnly}(및 findByUserIdAndReadIsFalse...)는 향후 미읽음 전용 뷰를
     *   대비한 예비 경로다 — 인덱스는 V9 에서 미리 깔아 두었다((user_id, is_read, sent_at)).
     */
    @Transactional(readOnly = true)
    public List<NotificationResult> getNotifications(Long userId, boolean unreadOnly) {
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findByUserIdAndReadIsFalseOrderBySentAtDesc(userId)
                : notificationRepository.findByUserIdOrderBySentAtDesc(userId);
        return notifications.stream().map(NotificationResult::from).toList();
    }

    /**
     * 내 알림을 읽는다. 없거나 내 것이 아니면 {@link ErrorCode#NOTIFICATION_NOT_FOUND}.
     * 읽음/삭제도 같은 판정을 쓰므로 패키지 내부에 공개한다.
     */
    Notification readOwned(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND); // 존재 은닉(타인 알림)
        }
        return notification;
    }
}
