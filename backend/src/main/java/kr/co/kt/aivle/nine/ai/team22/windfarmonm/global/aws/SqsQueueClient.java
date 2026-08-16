package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import jakarta.annotation.PreDestroy;
import java.util.List;

/**
 * 추론 파이프라인의 SQS 게이트웨이. 큐는 <b>둘</b>이며 역할이 다르다.
 * <ul>
 *   <li><b>요청 큐</b>(입력): 아웃박스 릴레이가 발사 요청을 넣고, 발사 폴러가 꺼내 SageMaker 를 호출한다.
 *       발사 실패 시 메시지를 삭제하지 않아 재배달되고, 반복 실패는 큐의 redrive 가 DLQ 로 격리한다 —
 *       이 관리형 재시도·격리가 입력 큐를 두는 이유다.</li>
 *   <li><b>결과 큐</b>(출력, windfarm-sqs): SNS(success/error)가 raw delivery 로 통보를 넣고,
 *       결과 폴러가 꺼내 결함을 적재한다.</li>
 * </ul>
 * 생산자·소비자·실패의 의미가 서로 달라 물리 큐를 합치지 않는다(한 큐면 소비자가 매 메시지의 종류를
 * 판별해야 하고 DLQ 에 요청 실패와 결과 실패가 섞인다).
 * <p>
 * {@link S3ObjectStorage} 와 같은 이유로 클라이언트를 <b>첫 사용 시점에</b> 만든다. 큐 URL/리전이 비어 있으면
 * 해당 {@code is*Configured()} 가 false 이고 폴러가 폴링 자체를 건너뛴다(앱은 정상 — 휴면 패턴).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AwsProperties.class)
public class SqsQueueClient {

    /** long-poll 대기(초). 빈 큐에서도 이 시간 안에 도착하면 즉시 반환된다 — 사실상 푸시급 지연. */
    private static final int WAIT_TIME_SECONDS = 10;
    private static final int MAX_MESSAGES = 10;

    private final AwsProperties properties;

    private volatile SqsClient client;

    /** 요청 큐(발사 대기) 설정 여부. */
    public boolean isRequestQueueConfigured() {
        return configured(properties.sqs().requestQueueUrl());
    }

    /** 결과 큐(추론 통보 수신) 설정 여부. */
    public boolean isResultQueueConfigured() {
        return configured(properties.sqs().resultQueueUrl());
    }

    /** 발사 요청을 요청 큐에 넣는다(아웃박스 릴레이 전용). */
    public void sendRequestMessage(String body) {
        client().sendMessage(SendMessageRequest.builder()
                .queueUrl(properties.sqs().requestQueueUrl())
                .messageBody(body)
                .build());
    }

    /** 요청 큐에서 발사 대기 메시지를 long-polling 으로 받아온다(최대 {@value MAX_MESSAGES}건). */
    public List<Message> receiveRequestMessages() {
        return receive(properties.sqs().requestQueueUrl());
    }

    /** 발사에 성공한 요청 메시지를 삭제한다. 미삭제면 재배달 → 반복 실패 시 요청 DLQ 로 격리된다. */
    public void deleteRequestMessage(String receiptHandle) {
        delete(properties.sqs().requestQueueUrl(), receiptHandle);
    }

    /** 결과 큐에서 메시지를 long-polling 으로 받아온다(최대 {@value MAX_MESSAGES}건). */
    public List<Message> receiveResultMessages() {
        return receive(properties.sqs().resultQueueUrl());
    }

    /** 처리 완료한 결과 메시지를 삭제한다. 삭제하지 않으면 visibility timeout 후 재배달된다. */
    public void deleteResultMessage(String receiptHandle) {
        delete(properties.sqs().resultQueueUrl(), receiptHandle);
    }

    private static boolean configuredUrl(String url) {
        return url != null && !url.isBlank();
    }

    private boolean configured(String url) {
        return properties.hasRegion() && configuredUrl(url);
    }

    private List<Message> receive(String queueUrl) {
        return client().receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .waitTimeSeconds(WAIT_TIME_SECONDS)
                        .maxNumberOfMessages(MAX_MESSAGES)
                        .build())
                .messages();
    }

    private void delete(String queueUrl, String receiptHandle) {
        client().deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build());
    }

    private SqsClient client() {
        SqsClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = SqsClient.builder()
                            .region(Region.of(properties.region()))
                            .httpClientBuilder(UrlConnectionHttpClient.builder())
                            .build();
                    client = local;
                }
            }
        }
        return local;
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }
}
