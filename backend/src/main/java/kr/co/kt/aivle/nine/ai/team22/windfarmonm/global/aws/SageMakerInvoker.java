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
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointAsyncRequest;
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
            // '미설정'과 구분되는 코드를 쓴다 — 타임아웃·권한오류를 설정 문제로 보고하면 원인 추적이 어긋난다.
            log.error("SageMaker 추론 호출 실패 endpoint={}", endpoint, e);
            throw new BusinessException(ErrorCode.INFERENCE_FAILURE);
        }
    }

    /** 결함탐지(Async) 엔드포인트 설정 여부. 릴레이가 발사 전 확인해 불필요한 예외를 피한다. */
    public boolean isDefectEndpointConfigured() {
        return properties.hasRegion()
                && properties.sagemaker().defectEndpoint() != null
                && !properties.sagemaker().defectEndpoint().isBlank();
    }

    /**
     * 결함탐지 엔드포인트를 <b>비동기(Async Inference)</b>로 호출한다. 입력은 본문이 아니라 S3 객체
     * 위치({@code s3://...})로 전달되고, 결과는 엔드포인트가 S3 에 쓴 뒤 SNS→SQS 로 통보된다 —
     * 이 메서드는 접수만 하고 즉시 반환한다.
     *
     * @param inputLocation 추론 입력 이미지의 S3 URI
     * @param inferenceId   결과 통보와 요청을 상관시키는 키(아웃박스 행 id). 통보 본문에 그대로 돌아온다
     */
    public void invokeDefectEndpointAsync(String inputLocation, String inferenceId) {
        if (!isDefectEndpointConfigured()) {
            throw new BusinessException(ErrorCode.INFERENCE_NOT_CONFIGURED);
        }
        String endpoint = properties.sagemaker().defectEndpoint();
        try {
            client().invokeEndpointAsync(InvokeEndpointAsyncRequest.builder()
                    .endpointName(endpoint)
                    .inputLocation(inputLocation)
                    .inferenceId(inferenceId)
                    .contentType("image/jpeg") // 드론 이미지 원본 — DL input_fn 이 jpeg/png 를 받는다
                    .build());
        } catch (RuntimeException e) {
            // 동기 호출과 동일 규약: 상세는 로그로만, '미설정'과 구분되는 코드로 실패를 보고한다.
            log.error("SageMaker 비동기 추론 접수 실패 endpoint={} inferenceId={}", endpoint, inferenceId, e);
            throw new BusinessException(ErrorCode.INFERENCE_FAILURE);
        }
    }

    private SageMakerRuntimeClient client() {
        SageMakerRuntimeClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    // 소켓 제한시간을 명시한다. SDK 기본값은 SageMaker 의 모델 응답 상한(60초)보다 짧아,
                    // 콜드스타트나 긴 추론이 성공할 수 있는데도 클라이언트가 먼저 끊어버린다.
                    //
                    // httpClient(이미 만든 인스턴스) 가 아니라 httpClientBuilder 를 넘긴다. 전자는 SDK 가
                    // 소유권을 갖지 않아 서비스 클라이언트를 close 해도 HTTP 클라이언트가 닫히지 않는다(연결 누수).
                    local = SageMakerRuntimeClient.builder()
                            .region(Region.of(properties.region()))
                            .httpClientBuilder(UrlConnectionHttpClient.builder()
                                    .socketTimeout(properties.sagemaker().invokeTimeout()))
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
