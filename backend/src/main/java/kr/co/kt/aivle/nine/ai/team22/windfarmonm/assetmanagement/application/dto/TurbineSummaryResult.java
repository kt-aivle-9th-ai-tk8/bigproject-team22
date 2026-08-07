package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

/**
 * 단지 상세조회의 터빈 요약 항목. model 은 TurbineModel.model 문자열.
 */
public record TurbineSummaryResult(
        Long id,
        Double latitude,
        Double longitude,
        String model,
        String code
) {
}
