package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.PowerPoint;

import java.time.LocalDateTime;

public record PowerPointResponse(
        LocalDateTime time,
        Double power
) {
    public static PowerPointResponse from(PowerPoint point) {
        return new PowerPointResponse(point.time(), point.power());
    }
}
