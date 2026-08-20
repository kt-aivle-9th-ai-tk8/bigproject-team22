package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.event;

/**
 * FE 가 이미지 업로드 완료를 통보해 점검이 INSPECTING 으로 전이됐음을 알린다.
 * 커밋 이후에 처리해야 하는 후속 작업(썸네일 생성)이 이 이벤트를 받는다.
 */
public record InspectionUploadCompleted(Long inspectionId) {
}
