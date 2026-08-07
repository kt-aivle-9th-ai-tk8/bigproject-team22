package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.Report;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportStatus;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;

import java.time.LocalDateTime;

/**
 * 보고서 단건(본문 포함).
 *
 * @param generatedAt 보고서 생성(접수) 시각. 스키마의 {@code generated_at} 을 접수 시각으로 쓴다(created_at 방언).
 */
public record ReportResult(
        Long id,
        Long windFarmId,
        Long turbineId,
        ReportType reportType,
        String title,
        String context,
        ReportStatus status,
        LocalDateTime generatedAt
) {
    public static ReportResult from(Report report) {
        return new ReportResult(
                report.getId(),
                report.getWindFarmId(),
                report.getTurbineId(),
                report.getReportType(),
                report.getTitle(),
                report.getContext(),
                report.getStatus(),
                report.getGeneratedAt());
    }
}
