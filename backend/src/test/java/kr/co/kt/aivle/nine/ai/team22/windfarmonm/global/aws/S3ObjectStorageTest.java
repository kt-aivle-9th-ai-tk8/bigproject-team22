package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 미설정 상태의 동작을 고정한다.
 * <p>
 * 핵심은 <b>기동을 막지 않는 것</b>이다. 설정이 없어도 빈 생성은 성공해야 하고, 실제 호출에서만 503 이 나야 한다.
 * (자격증명이 필요한 실제 S3 호출은 이 테스트의 검증 대상이 아니다.)
 */
class S3ObjectStorageTest {

    private static AwsProperties props(String region, String bucket) {
        return new AwsProperties(
                region,
                new AwsProperties.S3(bucket, "content", Duration.ofMinutes(15)),
                new AwsProperties.Sagemaker("", "", Duration.ofSeconds(70)),
                new AwsProperties.Sqs("", ""));
    }

    @Test
    @DisplayName("설정이 비어 있어도 빈 생성 자체는 성공한다(기동 차단 금지)")
    void construction_succeedsWithoutConfiguration() {
        S3ObjectStorage storage = new S3ObjectStorage(props("", ""));

        assertThat(storage.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("리전만 있고 버킷이 없으면 미설정으로 본다")
    void isConfigured_requiresBothRegionAndBucket() {
        assertThat(new S3ObjectStorage(props("ap-northeast-2", "")).isConfigured()).isFalse();
        assertThat(new S3ObjectStorage(props("", "some-bucket")).isConfigured()).isFalse();
        assertThat(new S3ObjectStorage(props("ap-northeast-2", "some-bucket")).isConfigured()).isTrue();
    }

    @Test
    @DisplayName("미설정 상태에서 업로드 URL 을 요청하면 503 STORAGE_NOT_CONFIGURED")
    void presignPut_notConfigured() {
        S3ObjectStorage storage = new S3ObjectStorage(props("", ""));

        assertThatThrownBy(() -> storage.presignPut("content/a.jpg", "image/jpeg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("미설정 상태에서 조회 URL/객체 읽기도 동일하게 503")
    void presignGetAndRead_notConfigured() {
        S3ObjectStorage storage = new S3ObjectStorage(props("", ""));

        assertThatThrownBy(() -> storage.presignGet("content/a.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_NOT_CONFIGURED);

        assertThatThrownBy(() -> storage.readUtf8("content/result.json"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("미설정 상태에서 객체 목록 조회도 동일하게 503")
    void listKeys_notConfigured() {
        S3ObjectStorage storage = new S3ObjectStorage(props("", ""));

        assertThatThrownBy(() -> storage.listKeys("content/inspections/1/"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_NOT_CONFIGURED);
    }
}
