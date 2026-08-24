package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation.dto;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto.UploadedImageResult;

import java.util.List;

/**
 * 점검 업로드 이미지 응답. 식별자는 전역 문자열-id 계약(ArchUnit)을 따른다.
 *
 * @param count 인식된 장수 — FE 가 자신이 업로드한 수와 대조해 누락을 즉시 발견할 수 있게 함께 준다.
 */
public record InspectionImagesResponse(int count, List<ImageItem> images) {

    public record ImageItem(String imagePath, String viewUrl, String bladeId, String partSide) {
    }

    public static InspectionImagesResponse from(List<UploadedImageResult> results) {
        return new InspectionImagesResponse(results.size(), results.stream()
                .map(r -> new ImageItem(r.imagePath(), r.viewUrl(),
                        r.bladeId() == null ? null : String.valueOf(r.bladeId()), r.partSide()))
                .toList());
    }
}
