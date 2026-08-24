package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SageMakerInvokerTest {

    private static AwsProperties props(String region, String endpoint) {
        return new AwsProperties(
                region,
                new AwsProperties.S3("", "content", Duration.ofMinutes(15)),
                new AwsProperties.Sagemaker(endpoint, "", Duration.ofSeconds(70)),
                new AwsProperties.Sqs("", ""));
    }

    @Test
    @DisplayName("설정이 비어 있어도 빈 생성 자체는 성공한다(기동 차단 금지)")
    void construction_succeedsWithoutConfiguration() {
        SageMakerInvoker invoker = new SageMakerInvoker(props("", ""));

        assertThat(invoker.isAnomalyEndpointConfigured()).isFalse();
    }

    @Test
    @DisplayName("리전과 엔드포인트가 모두 있어야 설정된 것으로 본다")
    void isConfigured_requiresBothRegionAndEndpoint() {
        assertThat(new SageMakerInvoker(props("ap-northeast-2", "")).isAnomalyEndpointConfigured()).isFalse();
        assertThat(new SageMakerInvoker(props("", "anomaly-ep")).isAnomalyEndpointConfigured()).isFalse();
        assertThat(new SageMakerInvoker(props("ap-northeast-2", "anomaly-ep")).isAnomalyEndpointConfigured()).isTrue();
    }

    @Test
    @DisplayName("미설정 상태에서 호출하면 503 INFERENCE_NOT_CONFIGURED")
    void invoke_notConfigured() {
        SageMakerInvoker invoker = new SageMakerInvoker(props("", ""));

        assertThatThrownBy(() -> invoker.invokeAnomalyEndpoint("{}"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INFERENCE_NOT_CONFIGURED);
    }
}
