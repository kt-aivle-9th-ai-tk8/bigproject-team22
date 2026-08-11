package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain;

/**
 * 점검 진행 상태. UPLOADING → INSPECTING → INSPECTED 순서로만 전이한다(값 검증은 애플리케이션 담당 — V5 주석).
 */
public enum InspectionStatus {

    /** 생성됨 — 드론 이미지가 presigned URL 로 업로드되는 중. */
    UPLOADING,

    /** 업로드 완료 통보됨 — DL 추론(결함 탐지)이 진행 중. */
    INSPECTING,

    /** 추론 결과(결함)가 적재 완료됨. */
    INSPECTED
}
