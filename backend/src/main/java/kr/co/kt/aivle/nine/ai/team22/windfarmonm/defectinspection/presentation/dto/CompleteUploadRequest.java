package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.presentation.dto;

/**
 * 업로드 완료 통보 본문(<b>선택</b>). 본문 없이 호출해도 기존과 동일하게 동작한다.
 *
 * @param uploadedCount FE 가 실제로 업로드한 장수. 주면 BE 가 S3 실측과 대조해 불일치 시 거부한다 —
 *                      진실의 원천은 여전히 S3 이고 이 값은 <b>기대값</b>으로만 쓰인다.
 *                      주지 않으면 대조를 건너뛴다(존재 확인만).
 */
public record CompleteUploadRequest(Integer uploadedCount) {

    /** 본문이 없거나 값이 비어 있으면 null — 호출측이 '대조 안 함'으로 읽는다. */
    public static Integer expectedCountOf(CompleteUploadRequest request) {
        return request == null ? null : request.uploadedCount();
    }
}
