package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port;

/**
 * 결함탐지 추론 발사 포트(SageMaker Async 위임). 소비자(defectinspection)가 소유한다.
 * 접수만 하고 즉시 반환하며, 결과는 SNS→SQS 통보로 돌아온다.
 */
public interface InferenceDispatchPort {

    /** 엔드포인트가 설정돼 있는지. 미설정이면 릴레이가 발사를 건너뛴다(아웃박스 PENDING 유지 — 휴면). */
    boolean isConfigured();

    /**
     * 이미지 1장의 추론을 접수한다.
     *
     * @param imageS3Uri  입력 이미지의 S3 URI
     * @param inferenceId 결과 통보와 상관시키는 키(아웃박스 행 id)
     */
    void dispatch(String imageS3Uri, String inferenceId);
}
