package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.application.port.NotificationRecipientPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.Notification;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 이상 보고서 발생 시 알림 fan-out. 수신자는 <b>ADMIN 전원 + 해당 단지 담당자</b>이며 수신자당 1행을 만든다.
 * ADMIN 이면서 담당자인 사용자가 두 번 받지 않도록 userId 로 중복 제거한다.
 * <p>
 * 현재 이 fan-out 의 유일한 호출자는 <b>이상감지 흐름(P7)</b>이다 — 이상 보고서를 자동 생성한 뒤 그 정보를
 * 넘겨 호출한다. P7 도입 전에는 트리거가 없어 알림이 생성되지 않는다(인박스는 비어 있음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationFanoutService {

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientPort recipientPort;

    /**
     * 이상 보고서 발생을 수신자들에게 알린다.
     *
     * @param reportId    발생 원인 이상 보고서 id
     * @param windFarmId  대상 단지(담당자 산출용)
     * @param reportTitle 발송 시점 제목 스냅샷
     */
    @Transactional
    public void notifyAnomalyReport(Long reportId, Long windFarmId, String reportTitle) {
        Set<Long> recipients = new LinkedHashSet<>(recipientPort.adminUserIds());
        recipients.addAll(recipientPort.assignedUserIds(windFarmId));
        if (recipients.isEmpty()) {
            log.warn("이상 보고서 {} 알림 수신자가 없다(ADMIN·담당자 0명) — 알림 미생성.", reportId);
            return;
        }
        List<Notification> notifications = recipients.stream()
                .map(userId -> Notification.of(userId, reportId, reportTitle))
                .toList();
        notificationRepository.saveAll(notifications);
        log.info("이상 보고서 {} 알림 {}건 발송.", reportId, notifications.size());
    }
}
