package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

/**
 * SageMaker Serverless Inference 동기 호출기.
 * <p>
 * 이상감지 배치가 매시각 이 엔드포인트를 호출해 판정 결과를 받는다. Serverless Inference 는 요청/응답
 * 동기 방식이므로 별도 수신 창구(webhook)가 필요 없다 — 부르는 쪽이 결과를 그 자리에서 받는다.
 * <p>
 * {@link S3ObjectStorage} 와 같은 이유로 <b>클라이언트를 첫 사용 시점에</b> 만든다. 엔드포인트 이름이나
 * 리전이 비어 있으면 {@link ErrorCode#INFERENCE_NOT_CONFIGURED}(503)로 해당 호출만 실패한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AwsProperties.class)
public class SageMakerInvoker {

    private static final String CONTENT_TYPE_JSON = "application/json";

    private final AwsProperties properties;

    private volatile SageMakerRuntimeClient client;

    /** 이상감지 엔드포인트 설정 여부. 스케줄러가 실행 전 확인해 불필요한 예외를 피한다. */
    public boolean isAnomalyEndpointConfigured() {
        return properties.hasRegion()
                && properties.sagemaker().anomalyEndpoint() != null
                && !properties.sagemaker().anomalyEndpoint().isBlank();
    }

    /**
     * 이상감지 엔드포인트를 호출하고 응답 본문을 그대로 반환한다.
     * <p>
     * 호출 시간이 엔드포인트 상한(콜드스타트 포함)을 넘지 않도록, 대상 범위를 단지 단위로 나눠 부르는 것은
     * 호출측 책임이다. 이 클래스는 전송만 담당한다.
     *
     * @param payloadJson 요청 본문(JSON)
     * @return 응답 본문(JSON)
     */
    public String invokeAnomalyEndpoint(String payloadJson) {
        if (!isAnomalyEndpointConfigured()) {
            throw new BusinessException(ErrorCode.INFERENCE_NOT_CONFIGURED);
        }
        String endpoint = properties.sagemaker().anomalyEndpoint();
        try {
            InvokeEndpointRequest request = InvokeEndpointRequest.builder()
                    .endpointName(endpoint)
                    .contentType(CONTENT_TYPE_JSON)
                    .accept(CONTENT_TYPE_JSON)
                    .body(SdkBytes.fromString(payloadJson, StandardCharsets.UTF_8))
                    .build();
            InvokeEndpointResponse response = client().invokeEndpoint(request);
            return response.body().asString(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            // 엔드포인트 이름 등 내부 정보가 응답에 새지 않도록 상세는 로그로만 남긴다.
            log.error("SageMaker 추론 호출 실패 endpoint={}", endpoint, e);
            throw new BusinessException(ErrorCode.INFERENCE_NOT_CONFIGURED);
        }
    }

    private SageMakerRuntimeClient client() {
        SageMakerRuntimeClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = SageMakerRuntimeClient.builder()
                            .region(Region.of(properties.region()))
                            .httpClient(UrlConnectionHttpClient.create())
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
