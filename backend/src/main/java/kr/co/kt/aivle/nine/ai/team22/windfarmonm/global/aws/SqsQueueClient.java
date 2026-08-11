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

import jakarta.annotation.PreDestroy;
import java.util.List;

/**
 * 추론 결과 큐(windfarm-sqs) 소비 게이트웨이. long-polling 수신과 삭제만 담당한다.
 * <p>
 * {@link S3ObjectStorage} 와 같은 이유로 클라이언트를 <b>첫 사용 시점에</b> 만든다. 큐 URL/리전이 비어 있으면
 * {@link #isConfigured()} 가 false 이고 폴러가 폴링 자체를 건너뛴다(앱은 정상 — 휴면 패턴).
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

    public boolean isConfigured() {
        return properties.hasRegion()
                && properties.sqs().resultQueueUrl() != null
                && !properties.sqs().resultQueueUrl().isBlank();
    }

    /** 결과 큐에서 메시지를 long-polling 으로 받아온다(최대 {@value MAX_MESSAGES}건). */
    public List<Message> receiveResultMessages() {
        return client().receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(properties.sqs().resultQueueUrl())
                        .waitTimeSeconds(WAIT_TIME_SECONDS)
                        .maxNumberOfMessages(MAX_MESSAGES)
                        .build())
                .messages();
    }

    /** 처리 완료한 메시지를 삭제한다. 삭제하지 않으면 visibility timeout 후 재배달된다(재시도 → DLQ redrive). */
    public void deleteResultMessage(String receiptHandle) {
        client().deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(properties.sqs().resultQueueUrl())
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
