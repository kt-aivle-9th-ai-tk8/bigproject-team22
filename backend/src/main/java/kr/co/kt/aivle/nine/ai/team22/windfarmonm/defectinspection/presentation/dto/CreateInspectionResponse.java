package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.CreateInspectionResult;

import java.util.List;

/**
 * 드론 점검 세션 생성 응답(명세 구조 그대로). 터빈별 inspection_id 와 블레이드별 부위 업로드 URL 목록,
 * 세션 공유 report_id. FE 는 각 URL 로 {@code Content-Type: image/jpeg} PUT 을 수행한 뒤
 * 터빈(점검)별로 업로드 완료를 통보한다.
 */
public record CreateInspectionResponse(String windFarmId, List<TurbineItem> turbines, String reportId) {

    public record TurbineItem(String turbineId, String inspectionId, List<BladeItem> blades) {
    }

    public record BladeItem(String bladeId,
                            List<String> leadingEdgeUploadUrls, List<String> pressureSideUploadUrls,
                            List<String> suctionSideUploadUrls, List<String> trailingEdgeUploadUrls) {
    }

    public static CreateInspectionResponse from(CreateInspectionResult result) {
        List<TurbineItem> turbines = result.turbines().stream()
                .map(turbine -> new TurbineItem(
                        String.valueOf(turbine.turbineId()),
                        String.valueOf(turbine.inspectionId()),
                        turbine.blades().stream()
                                .map(blade -> new BladeItem(
                                        String.valueOf(blade.bladeId()),
                                        blade.leadingEdgeUploadUrls(), blade.pressureSideUploadUrls(),
                                        blade.suctionSideUploadUrls(), blade.trailingEdgeUploadUrls()))
                                .toList()))
                .toList();
        return new CreateInspectionResponse(
                String.valueOf(result.windFarmId()), turbines, String.valueOf(result.reportId()));
    }
}
