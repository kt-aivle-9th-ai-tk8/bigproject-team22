package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.ReportSummaryResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportStatus;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;

import java.time.LocalDateTime;

/**
 * 보고서 목록 항목. 본문({@code context})은 목록에서 제외한다 — 마크다운 전문이 실려 응답이 비대해진다.
 */
public record ReportSummaryResponse(
        String id,
        String windFarmId,
        String turbineId,
        ReportType reportType,
        String title,
        ReportStatus status,
        LocalDateTime generatedAt
) {
    public static ReportSummaryResponse from(ReportSummaryResult result) {
        return new ReportSummaryResponse(
                String.valueOf(result.id()),
                String.valueOf(result.windFarmId()),
                result.turbineId() == null ? null : String.valueOf(result.turbineId()),
                result.reportType(),
                result.title(),
                result.status(),
                result.generatedAt());
    }
}
