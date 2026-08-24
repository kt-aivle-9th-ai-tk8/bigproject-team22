package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.dto.CreateReportCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.domain.ReportType;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.web.ApiIds;

import java.time.LocalDateTime;

/**
 * 보고서 초안 생성 요청.
 * <p>
 * 식별자는 계약상 문자열로 받는다(JS Number 정밀도 손실 방지). 도메인 타입 변환은 {@link ApiIds} 가 맡는다.
 *
 * @param turbineId 단지 운영 보고서에는 지정하지 않는다(그 외 유형에는 필수)
 */
public record CreateReportRequest(
        @NotBlank(message = "발전소 id 는 필수입니다.")
        String windFarmId,

        String turbineId,

        @NotNull(message = "보고서 유형은 필수입니다.")
        ReportType reportType,

        @NotNull(message = "대상 기간 시작일시는 필수입니다.")
        LocalDateTime periodStart,

        @NotNull(message = "대상 기간 종료일시는 필수입니다.")
        LocalDateTime periodEnd
) {
    public CreateReportCommand toCommand() {
        return new CreateReportCommand(
                reportType,
                ApiIds.toLong(windFarmId),
                turbineId == null || turbineId.isBlank() ? null : ApiIds.toLong(turbineId),
                periodStart,
                periodEnd,
                null); // 사용자 요청 생성이므로 유발 이벤트가 없다
    }
}
