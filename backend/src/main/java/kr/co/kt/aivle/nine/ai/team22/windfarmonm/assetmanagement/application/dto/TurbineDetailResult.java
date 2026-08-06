package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

import java.util.List;

/**
 * 터빈 3D 뷰어 통합조회 결과.
 */
public record TurbineDetailResult(
        Long id,
        Double latitude,
        Double longitude,
        String model,
        String code,
        PowerSummary power,
        List<BladeSummaryResult> blades
) {
}
