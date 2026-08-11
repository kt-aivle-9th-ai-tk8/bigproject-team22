package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결함 이미지 1장(이미지 단위 그룹핑 — 명세: GET /blades/{id}/defect-images).
 *
 * @param thumbnailUrl presigned GET URL(담당 인가를 통과한 요청에만 발급)
 * @param maxSeverity  이미지 내 결함들의 최대 심각도(전부 null 이면 null)
 * @param createdAt    적재 시각(이미지 내 첫 결함 기준)
 */
public record DefectImageResult(String imagePath, String thumbnailUrl, List<DefectItem> defects,
                                Integer maxSeverity, String partSide, LocalDateTime createdAt) {

    /** 이미지 속 결함 1건(바운딩 박스 좌표계는 원본 픽셀). */
    public record DefectItem(Long id, String type, Integer severity,
                             Double bboxX, Double bboxY, Double bboxW, Double bboxH, Double confidence) {
    }
}
