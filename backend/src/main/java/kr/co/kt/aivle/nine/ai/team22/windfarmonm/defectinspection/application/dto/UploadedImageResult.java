package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.dto;

/**
 * 업로드된 이미지 1장(S3 원천 기준). 추론 전에도 조회되므로 결함 정보는 담지 않는다.
 *
 * @param viewUrl presigned GET URL(담당 인가를 통과한 요청에만 발급)
 */
public record UploadedImageResult(String imagePath, String viewUrl, Long bladeId, String partSide) {
}
