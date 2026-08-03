package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.BladeSummaryResult;

public record BladeResponse(
        String id,
        String tag
) {
    public static BladeResponse from(BladeSummaryResult result) {
        return new BladeResponse(result.id().toString(), result.tag());
    }
}
