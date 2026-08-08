package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;

import java.time.LocalDateTime;

/**
 * 보고서 생성 명령.
 *
 * @param turbineId     단지 단위 보고서는 null
 * @param anomalyEventId 자동 생성을 유발한 이상감지 이벤트. 사용자 요청 생성은 null
 */
public record CreateReportCommand(
        ReportType reportType,
        Long windFarmId,
        Long turbineId,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        Long anomalyEventId
) {
}
