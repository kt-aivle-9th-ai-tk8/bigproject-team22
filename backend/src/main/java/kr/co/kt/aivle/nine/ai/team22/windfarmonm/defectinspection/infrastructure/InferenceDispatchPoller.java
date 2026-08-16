package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws.SageMakerInvoker;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws.SqsQueueClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 발사 폴러. 요청 큐를 long-polling 으로 소비해 SageMaker Async 추론을 접수한다.
 * <p>
 * 아웃박스 릴레이가 넣은 메시지({@code {inference_id, image_s3_uri}})를 꺼내
 * {@code InvokeEndpointAsync} 를 호출하고, 성공한 메시지만 삭제한다. 접수 실패 시 삭제하지 않아
 * visibility timeout 후 재배달되며, 반복 실패는 큐의 redrive 정책이 요청 DLQ 로 격리한다 —
 * 관리형 재시도·격리를 얻는 것이 발사 경로에 큐를 둔 이유다.
 * <p>
 * 접수는 짧은 API 호출이라 DB 를 건드리지 않는다(그래서 트랜잭션이 없다). 같은 메시지가 중복 배달되어
 * 두 번 접수되더라도 {@code inferenceId} 가 같아 결과 처리 쪽 멱등 가드(COMPLETED skip)가 흡수한다.
 * 큐나 엔드포인트가 미설정이면 폴링 자체를 건너뛴다(CI·로컬 무해).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InferenceDispatchPoller {

    private final SqsQueueClient sqsQueueClient;
    private final SageMakerInvoker sageMakerInvoker;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "PT2S")
    @SchedulerLock(name = "defect-dispatch-poller", lockAtMostFor = "PT2M")
    public void poll() {
        if (!sqsQueueClient.isRequestQueueConfigured() || !sageMakerInvoker.isDefectEndpointConfigured()) {
            return; // 큐/엔드포인트 미설정 — 휴면(요청은 큐에 남아 설정 후 소비된다)
        }
        List<Message> messages = sqsQueueClient.receiveRequestMessages();
        for (Message message : messages) {
            try {
                if (dispatch(message.body())) {
                    sqsQueueClient.deleteRequestMessage(message.receiptHandle());
                }
            } catch (RuntimeException e) {
                // 미삭제 → 재배달(일시 장애 재시도). 반복 실패는 redrive 가 DLQ 로 옮긴다.
                log.warn("추론 발사 실패 — 재배달 대기(messageId={})", message.messageId(), e);
            }
        }
    }

    /**
     * 메시지 1건 발사.
     *
     * @return true 면 종결(삭제 가능). 규약 밖 메시지도 true 다 — 재배달해도 같은 실패를 반복하므로
     *         큐에 남겨 두면 DLQ 만 오염시킨다. 일시 장애는 예외로 올라가 미삭제된다.
     */
    private boolean dispatch(String body) {
        JsonNode node;
        try {
            node = objectMapper.readTree(body);
        } catch (JacksonException e) {
            // 깨진 JSON 은 재배달해도 같은 실패다 — 큐에 남기면 DLQ 만 오염시킨다.
            log.error("요청 큐 메시지가 JSON 이 아니다 — 폐기: {}", body, e);
            return true;
        }
        String inferenceId = node.path("inference_id").asString(null);
        String imageS3Uri = node.path("image_s3_uri").asString(null);
        if (inferenceId == null || imageS3Uri == null) {
            log.error("요청 큐 메시지 형식이 규약 밖이다 — 폐기: {}", body);
            return true;
        }
        sageMakerInvoker.invokeDefectEndpointAsync(imageS3Uri, inferenceId);
        return true;
    }
}
