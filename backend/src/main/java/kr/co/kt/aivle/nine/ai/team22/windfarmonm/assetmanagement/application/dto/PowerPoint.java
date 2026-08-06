package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

import java.time.LocalDateTime;

/**
 * 발전량 시계열 한 점. time(집계 시각), power(집계 발전량).
 */
public record PowerPoint(
        LocalDateTime time,
        Double power
) {
}
