package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportStatus;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;

import java.time.LocalDateTime;

/**
 * 보고서 단건 응답(본문 포함). 식별자는 문자열로 직렬화한다.
 *
 * @param turbineId 단지 단위 보고서는 null
 * @param context   생성 완료 전에는 null
 */
public record ReportDetailResponse(
        String id,
        String windFarmId,
        String turbineId,
        ReportType reportType,
        String title,
        String context,
        ReportStatus status,
        LocalDateTime generatedAt
) {
    public static ReportDetailResponse from(ReportResult result) {
        return new ReportDetailResponse(
                String.valueOf(result.id()),
                String.valueOf(result.windFarmId()),
                result.turbineId() == null ? null : String.valueOf(result.turbineId()),
                result.reportType(),
                result.title(),
                result.context(),
                result.status(),
                result.generatedAt());
    }
}
