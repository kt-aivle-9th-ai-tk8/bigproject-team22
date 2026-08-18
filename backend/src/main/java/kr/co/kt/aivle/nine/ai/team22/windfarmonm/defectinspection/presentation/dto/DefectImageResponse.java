package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.DefectImageResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결함 이미지 응답(명세: GET /blades/{blade_id}/defect-images — 이미지 단위 그룹핑).
 * 결함 id 는 전역 문자열-id 계약(ArchUnit)을 따른다.
 */
public record DefectImageResponse(String imagePath, String thumbnailUrl, List<DefectItem> defects,
                                  Integer maxSeverity, String partSide, LocalDateTime createdAt) {

    public record DefectItem(String id, String type, Integer severity,
                             Double bboxX, Double bboxY, Double bboxW, Double bboxH, Double confidence) {
    }

    public static DefectImageResponse from(DefectImageResult result) {
        return new DefectImageResponse(
                result.imagePath(),
                result.thumbnailUrl(),
                result.defects().stream()
                        .map(d -> new DefectItem(String.valueOf(d.id()), d.type(), d.severity(),
                                d.bboxX(), d.bboxY(), d.bboxW(), d.bboxH(), d.confidence()))
                        .toList(),
                result.maxSeverity(),
                result.partSide(),
                result.createdAt());
    }
}
