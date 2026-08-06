package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * AWS 연동 설정({@code aws.*}).
 * <p>
 * 모든 값에 기본값을 두어 <b>미설정 상태에서도 애플리케이션이 정상 기동</b>하게 한다. 실제 접근이 일어나는
 * 엔드포인트만 503 으로 실패한다. 자격증명은 여기서 다루지 않는다 — 운영에서는 ECS Task Role 을,
 * 로컬에서는 개발자 프로파일을 AWS SDK 기본 자격증명 체인이 알아서 집는다.
 *
 * @param region 리전. 비어 있으면 미설정으로 간주한다.
 */
@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
        @DefaultValue("") String region,
        @DefaultValue S3 s3,
        @DefaultValue Sagemaker sagemaker
) {

    /**
     * @param bucket     드론 촬영 이미지/분석 결과 버킷. 비어 있으면 미설정.
     * @param prefix     버킷 내 최상위 경로. 키 규약은 {@code {prefix}/inspections/{id}/...}
     * @param presignTtl presigned URL 유효기간. 업로드는 다건이라 넉넉히 잡는다.
     */
    public record S3(
            @DefaultValue("") String bucket,
            @DefaultValue("content") String prefix,
            @DefaultValue("15m") Duration presignTtl
    ) {
    }

    /**
     * @param anomalyEndpoint 이상감지 Serverless Inference 엔드포인트 이름. 비어 있으면 미설정.
     * @param invokeTimeout   호출 소켓 제한시간. SageMaker 는 모델 응답 상한이 60초이므로 그보다 넉넉히 잡는다 —
     *                        더 짧으면 성공할 수 있는 추론을 클라이언트가 먼저 끊어버린다(콜드스타트 포함).
     */
    public record Sagemaker(
            @DefaultValue("") String anomalyEndpoint,
            @DefaultValue("70s") Duration invokeTimeout
    ) {
    }

    public boolean hasRegion() {
        return region != null && !region.isBlank();
    }
}
