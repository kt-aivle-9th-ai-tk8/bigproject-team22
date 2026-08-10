package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.Notification;

import java.time.LocalDateTime;

/**
 * 알림 목록 항목.
 *
 * @param reportId 발생 원인 보고서. 보고서가 삭제됐으면 null(제목 스냅샷은 남는다)
 */
public record NotificationResult(
        Long id,
        Long reportId,
        String reportTitle,
        boolean read,
        LocalDateTime sentAt
) {
    public static NotificationResult from(Notification notification) {
        return new NotificationResult(
                notification.getId(),
                notification.getReportId(),
                notification.getReportTitle(),
                notification.isRead(),
                notification.getSentAt());
    }
}
