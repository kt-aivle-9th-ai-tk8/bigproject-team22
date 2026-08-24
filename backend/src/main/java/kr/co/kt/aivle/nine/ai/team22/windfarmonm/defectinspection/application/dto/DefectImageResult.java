package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결함 이미지 1장(이미지 단위 그룹핑 — 명세: GET /blades/{id}/defect-images).
 *
 * @param imagePath    S3 키. <b>내부 그룹핑 키</b>이며 응답에는 나가지 않는다(저장소 구조가 드러난다)
 * @param imageUrl     원본 이미지 presigned GET URL(담당 인가를 통과한 요청에만 발급)
 * @param thumbnailUrl 썸네일 presigned GET URL. 썸네일 생성 전까지는 원본과 같은 URL 이다(이슈 #131)
 * @param maxSeverity  이미지 내 결함들의 최대 심각도(전부 null 이면 null)
 * @param createdAt    적재 시각(이미지 내 첫 결함 기준)
 */
public record DefectImageResult(String imagePath, String imageUrl, String thumbnailUrl, List<DefectItem> defects,
                                Integer maxSeverity, String partSide, LocalDateTime createdAt) {

    /**
     * 이미지 속 결함 1건.
     * <p>
     * 바운딩 박스는 <b>원본 이미지의 픽셀 좌표</b>(x, y, w, h)다. 화면 크기에 맞춘 비율(0~1)로 주지
     * 않는 이유는 그러려면 원본 크기를 함께 저장·전달해야 하는데, 표시측이 이미지를 로드하면
     * naturalWidth/naturalHeight 로 같은 값을 얻을 수 있어서다.
     */
    public record DefectItem(Long id, String type, Integer severity,
                             Double bboxX, Double bboxY, Double bboxW, Double bboxH, Double confidence) {
    }
}
