package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.DefectImageResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결함 이미지 응답(명세: GET /blades/{blade_id}/defect-images — 이미지 단위 그룹핑).
 * 결함 id 는 전역 문자열-id 계약(ArchUnit)을 따른다.
 * <p>
 * <b>S3 키(image_path)는 내보내지 않는다.</b> 링크가 아니라 저장소 내부 경로라 그대로는 열 수 없고,
 * 버킷 구조를 노출한다. 열람은 {@code image_url}/{@code thumbnail_url} 의 presigned URL 로만 한다.
 */
public record DefectImageResponse(String imageUrl, String thumbnailUrl, List<DefectItem> defects,
                                  Integer maxSeverity, String partSide, LocalDateTime createdAt) {

    /** bbox 는 원본 이미지의 픽셀 좌표(x, y, w, h)다 — 표시 크기에 맞추려면 naturalWidth/Height 로 나눌 것. */
    public record DefectItem(String id, String type, Integer severity,
                             Double bboxX, Double bboxY, Double bboxW, Double bboxH, Double confidence) {
    }

    public static DefectImageResponse from(DefectImageResult result) {
        return new DefectImageResponse(
                result.imageUrl(),
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
