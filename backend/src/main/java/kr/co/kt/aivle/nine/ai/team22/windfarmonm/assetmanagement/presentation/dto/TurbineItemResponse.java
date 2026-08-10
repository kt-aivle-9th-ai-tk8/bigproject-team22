package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.TurbineSummaryResult;

public record TurbineItemResponse(
        String id,
        Double latitude,
        Double longitude,
        String model,
        String code,
        Double currentPower
) {
    public static TurbineItemResponse from(TurbineSummaryResult result) {
        return new TurbineItemResponse(
                result.id().toString(),
                result.latitude(),
                result.longitude(),
                result.model(),
                result.code(),
                result.currentPower()
        );
    }
}
