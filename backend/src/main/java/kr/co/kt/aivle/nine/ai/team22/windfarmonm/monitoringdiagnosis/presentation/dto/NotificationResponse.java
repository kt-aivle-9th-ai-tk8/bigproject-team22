package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.application.dto.NotificationResult;

import java.time.LocalDateTime;

/**
 * 알림 목록 항목 응답. 식별자는 문자열로 직렬화한다(JS 정밀도 손실 방지).
 *
 * @param reportId 발생 원인 보고서. 보고서가 삭제됐으면 null(제목 스냅샷은 유지)
 */
public record NotificationResponse(
        String id,
        String reportId,
        String reportTitle,
        boolean isRead,
        LocalDateTime sentAt
) {
    public static NotificationResponse from(NotificationResult result) {
        return new NotificationResponse(
                String.valueOf(result.id()),
                result.reportId() == null ? null : String.valueOf(result.reportId()),
                result.reportTitle(),
                result.read(),
                result.sentAt());
    }
}
