package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

/**
 * 사용자에게 배정된 단지(식별자 + 표시명).
 */
public record AssignedWindFarmResult(
        Long windFarmId,
        String windFarmName
) {
}
