package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws.SageMakerInvoker;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws.SqsQueueClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 발사 경로(요청 큐 적재 → 폴러가 SageMaker 접수)를 검증한다. 큐를 거치는 이유가 실패 격리이므로,
 * "성공한 메시지만 삭제한다"는 규약이 이 테스트의 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
class InferenceDispatchTest {

    /** 운영과 동일한 snake_case 직렬화 — 메시지 계약을 그대로 검증한다. */
    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Mock
    SqsQueueClient sqsQueueClient;

    @Nested
    @DisplayName("적재(어댑터)")
    class Adapter {

        InferenceDispatchAdapter adapter;

        @BeforeEach
        void setUp() {
            adapter = new InferenceDispatchAdapter(sqsQueueClient, mapper);
        }

        @Test
        @DisplayName("SageMaker 를 직접 부르지 않고 요청 큐에 snake_case 메시지로 적재한다")
        void dispatch_sendsToRequestQueue() {
            adapter.dispatch("s3://bucket/content/inspections/5/31/LE/1.jpg", "77");

            ArgumentCaptor<String> body = ArgumentCaptor.captor();
            verify(sqsQueueClient).sendRequestMessage(body.capture());
            assertThat(body.getValue())
                    .contains("\"inference_id\":\"77\"")
                    .contains("\"image_s3_uri\":\"s3://bucket/content/inspections/5/31/LE/1.jpg\"");
        }

        @Test
        @DisplayName("설정 여부는 요청 큐 기준이다(엔드포인트가 아니라 큐가 발사의 입구)")
        void isConfigured_followsRequestQueue() {
            when(sqsQueueClient.isRequestQueueConfigured()).thenReturn(false);
            assertThat(adapter.isConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("발사(폴러)")
    class Poller {

        @Mock
        SageMakerInvoker sageMakerInvoker;

        InferenceDispatchPoller poller;

        @BeforeEach
        void setUp() {
            poller = new InferenceDispatchPoller(sqsQueueClient, sageMakerInvoker, mapper);
        }

        private Message message(String body) {
            return Message.builder().messageId("m1").receiptHandle("r1").body(body).build();
        }

        private void queueReady() {
            when(sqsQueueClient.isRequestQueueConfigured()).thenReturn(true);
            when(sageMakerInvoker.isDefectEndpointConfigured()).thenReturn(true);
        }

        @Test
        @DisplayName("메시지를 꺼내 SageMaker 로 접수하고, 성공한 것만 삭제한다")
        void poll_invokesAndDeletes() {
            queueReady();
            when(sqsQueueClient.receiveRequestMessages()).thenReturn(List.of(message(
                    "{\"inference_id\":\"77\",\"image_s3_uri\":\"s3://bucket/img.jpg\"}")));

            poller.poll();

            verify(sageMakerInvoker).invokeDefectEndpointAsync("s3://bucket/img.jpg", "77");
            verify(sqsQueueClient).deleteRequestMessage("r1");
        }

        @Test
        @DisplayName("접수 실패는 삭제하지 않는다 — 재배달 후 반복 실패 시 DLQ 로 격리된다")
        void poll_keepsMessageOnFailure() {
            queueReady();
            when(sqsQueueClient.receiveRequestMessages()).thenReturn(List.of(message(
                    "{\"inference_id\":\"77\",\"image_s3_uri\":\"s3://bucket/img.jpg\"}")));
            doThrow(new RuntimeException("endpoint down"))
                    .when(sageMakerInvoker).invokeDefectEndpointAsync(any(), any());

            poller.poll();

            verify(sqsQueueClient, never()).deleteRequestMessage(any());
        }

        @Test
        @DisplayName("규약 밖 메시지는 폐기한다(재배달해도 같은 실패라 DLQ 만 오염시킨다)")
        void poll_discardsMalformed() {
            queueReady();
            when(sqsQueueClient.receiveRequestMessages())
                    .thenReturn(List.of(message("{\"foo\":\"bar\"}")));

            poller.poll();

            verify(sageMakerInvoker, never()).invokeDefectEndpointAsync(any(), any());
            verify(sqsQueueClient).deleteRequestMessage("r1");
        }

        @Test
        @DisplayName("깨진 JSON 메시지는 폐기한다 — 재배달하면 같은 실패로 DLQ 만 오염된다")
        void poll_discardsMalformedJson() {
            queueReady();
            when(sqsQueueClient.receiveRequestMessages()).thenReturn(List.of(message("{not-json")));

            poller.poll();   // 예외가 새지 않아야 한다

            verify(sageMakerInvoker, never()).invokeDefectEndpointAsync(any(), any());
            verify(sqsQueueClient).deleteRequestMessage("r1");
        }

        @Test
        @DisplayName("큐나 엔드포인트가 미설정이면 폴링 자체를 건너뛴다(휴면)")
        void poll_skipsWhenUnconfigured() {
            when(sqsQueueClient.isRequestQueueConfigured()).thenReturn(false);

            poller.poll();

            verify(sqsQueueClient, never()).receiveRequestMessages();
        }
    }
}
