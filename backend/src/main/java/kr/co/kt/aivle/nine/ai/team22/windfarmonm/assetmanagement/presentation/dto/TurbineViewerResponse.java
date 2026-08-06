package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.TurbineDetailResult;

import java.util.List;

/**
 * 터빈 3D 뷰어 통합조회 응답. (결함 레드닷 좌표/AI 텍스처는 MVP 범위 외로 제외)
 */
public record TurbineViewerResponse(
        String id,
        Double latitude,
        Double longitude,
        String model,
        String code,
        PowerResponse power,
        List<BladeResponse> blades
) {
    public static TurbineViewerResponse from(TurbineDetailResult result) {
        return new TurbineViewerResponse(
                result.id().toString(),
                result.latitude(),
                result.longitude(),
                result.model(),
                result.code(),
                PowerResponse.from(result.power()),
                result.blades().stream().map(BladeResponse::from).toList()
        );
    }
}
