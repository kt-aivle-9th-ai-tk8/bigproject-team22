package kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.dto;

/**
 * 블레이드 식별 정보(내부 조회용). 점검(defectinspection)이 요청의 블레이드 태그(A/B/C)를
 * blade_id 로 해석할 때 쓴다.
 */
public record BladeIdentity(Long id, String tag) {
}
