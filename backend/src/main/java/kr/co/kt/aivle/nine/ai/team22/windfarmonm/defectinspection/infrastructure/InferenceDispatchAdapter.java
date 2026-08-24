package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InferenceDispatchPort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws.SqsQueueClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link InferenceDispatchPort} 어댑터. 발사 요청을 <b>요청 큐에 적재</b>한다.
 * <p>
 * SageMaker 를 여기서 직접 부르지 않는 이유는 실패 처리다 — 큐에 넣어 두면 발사 실패(엔드포인트 장애·
 * 스로틀 등)를 SQS 의 재배달로 재시도하고, 반복 실패는 redrive 가 요청 DLQ 로 격리한다. 직접 호출은
 * 아웃박스가 PENDING 으로 무한 재시도할 뿐 격리 지점이 없다. 실제 호출은
 * {@link InferenceDispatchPoller} 가 큐를 소비하며 수행한다.
 * <p>
 * 메시지 본문은 전역 snake_case 전략으로 {@code {"inference_id":"...","image_s3_uri":"s3://..."}} 가 된다.
 */
@Component
@RequiredArgsConstructor
public class InferenceDispatchAdapter implements InferenceDispatchPort {

    private final SqsQueueClient sqsQueueClient;
    private final ObjectMapper objectMapper;

    @Override
    public boolean isConfigured() {
        return sqsQueueClient.isRequestQueueConfigured();
    }

    @Override
    public void dispatch(String imageS3Uri, String inferenceId) {
        sqsQueueClient.sendRequestMessage(
                objectMapper.writeValueAsString(new DispatchMessage(inferenceId, imageS3Uri)));
    }

    /** 요청 큐 메시지. 발사 폴러가 이 두 값만으로 SageMaker 를 호출할 수 있어야 한다(자족적). */
    record DispatchMessage(String inferenceId, String imageS3Uri) {
    }
}
