package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.CreateInspectionCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.web.ApiIds;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 드론 점검 세션 생성 요청(명세: POST /api/inspections). 식별자는 문자열 계약(ArchUnit — 중첩 record 포함).
 * 부위별 이미지 수는 명세의 4개 필드(leading_edge/pressure_side/suction_side/trailing_edge)_count.
 * 촬영 기간(inspection_start/end)은 명세 누락분으로 합의 구현 — RDS 컬럼명(inspection.inspection_start/end) 그대로.
 *
 * @param context 보고서 참고사항(선택)
 */
public record CreateInspectionRequest(
        @NotBlank String windFarmId,
        @NotNull LocalDateTime inspectionStart,
        @NotNull LocalDateTime inspectionEnd,
        @NotEmpty @Valid List<TurbineGroup> turbines,
        String context) {

    public record TurbineGroup(
            @NotBlank String turbineId,
            @NotEmpty @Valid List<BladeGroup> blades) {
    }

    public record BladeGroup(
            @NotBlank String bladeId,
            @NotNull Integer leadingEdgeCount,
            @NotNull Integer pressureSideCount,
            @NotNull Integer suctionSideCount,
            @NotNull Integer trailingEdgeCount) {
    }

    public CreateInspectionCommand toCommand() {
        List<CreateInspectionCommand.TurbineSpec> turbineSpecs = turbines.stream()
                .map(turbine -> new CreateInspectionCommand.TurbineSpec(
                        ApiIds.toLong(turbine.turbineId()),
                        turbine.blades().stream()
                                .map(blade -> new CreateInspectionCommand.BladeSpec(
                                        ApiIds.toLong(blade.bladeId()),
                                        blade.leadingEdgeCount(), blade.pressureSideCount(),
                                        blade.suctionSideCount(), blade.trailingEdgeCount()))
                                .toList()))
                .toList();
        return new CreateInspectionCommand(
                ApiIds.toLong(windFarmId), inspectionStart, inspectionEnd, turbineSpecs, context);
    }
}
