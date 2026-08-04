package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.domain.Blade;

/**
 * 터빈 3D 뷰어의 블레이드 요약 항목.
 */
public record BladeSummaryResult(
        Long id,
        String tag
) {
    public static BladeSummaryResult from(Blade blade) {
        return new BladeSummaryResult(blade.getId(), blade.getTag());
    }
}
