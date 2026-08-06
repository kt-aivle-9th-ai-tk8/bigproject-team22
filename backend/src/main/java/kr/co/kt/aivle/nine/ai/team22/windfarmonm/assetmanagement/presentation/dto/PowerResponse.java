package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto.PowerSummary;

public record PowerResponse(
        Double currentPower,
        Double todayPower,
        Double monthPower
) {
    public static PowerResponse from(PowerSummary summary) {
        if (summary == null) {
            return null;
        }
        return new PowerResponse(summary.currentPower(), summary.todayPower(), summary.monthPower());
    }
}
