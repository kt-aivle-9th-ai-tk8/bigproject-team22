package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

/**
 * S3 접근 게이트웨이. presigned URL 발급과 객체 읽기를 담당한다.
 * <p>
 * <b>클라이언트를 생성자에서 만들지 않는다.</b> AWS SDK 의 기본 자격증명 체인은 EC2/ECS 메타데이터를 조회하는데,
 * 자격증명이 없는 환경(로컬·CI)에서는 이 조회가 수 초 지연되거나 실패한다. 빈 생성 시점에 이를 수행하면
 * 애플리케이션 기동 자체가 늦어지거나 깨진다. 과거 리포트 에이전트가 모듈 로드 시점에 외부 클라이언트를 세우다
 * 컨테이너 기동에 실패해 배포가 롤백된 전례가 있어, 여기서는 <b>첫 사용 시점에</b> 만들고 캐싱한다.
 * <p>
 * 버킷/리전이 비어 있으면 {@link ErrorCode#STORAGE_NOT_CONFIGURED}(503)로 해당 요청만 실패시킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AwsProperties.class)
public class S3ObjectStorage {

    private final AwsProperties properties;

    private volatile S3Client client;
    private volatile S3Presigner presigner;

    /** 설정이 갖춰졌는지. 호출측이 기능 자체를 건너뛰고 싶을 때 쓴다(예: 썸네일 없이 응답). */
    public boolean isConfigured() {
        return properties.hasRegion()
                && properties.s3().bucket() != null
                && !properties.s3().bucket().isBlank();
    }

    /** 업로드용 presigned URL. 드론 이미지를 클라이언트가 S3 에 직접 올리도록 한다. */
    public String presignPut(String key, String contentType) {
        requireConfigured();
        try {
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(properties.s3().bucket())
                    .key(key)
                    .contentType(contentType)
                    .build();
            return presigner().presignPutObject(PutObjectPresignRequest.builder()
                            .signatureDuration(properties.s3().presignTtl())
                            .putObjectRequest(put)
                            .build())
                    .url()
                    .toString();
        } catch (RuntimeException e) {
            throw storageFailure("업로드 URL 발급 실패 key={}", key, e);
        }
    }

    /** 조회용 presigned URL. 결함 이미지 썸네일 링크가 이 경로로 발급된다. */
    public String presignGet(String key) {
        requireConfigured();
        try {
            GetObjectRequest get = GetObjectRequest.builder()
                    .bucket(properties.s3().bucket())
                    .key(key)
                    .build();
            return presigner().presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(properties.s3().presignTtl())
                            .getObjectRequest(get)
                            .build())
                    .url()
                    .toString();
        } catch (RuntimeException e) {
            throw storageFailure("조회 URL 발급 실패 key={}", key, e);
        }
    }

    /** 객체를 UTF-8 문자열로 읽는다. 비전 분석 결과 JSON 을 가져올 때 쓴다. */
    public String readUtf8(String key) {
        requireConfigured();
        try {
            GetObjectRequest get = GetObjectRequest.builder()
                    .bucket(properties.s3().bucket())
                    .key(key)
                    .build();
            return client().getObjectAsBytes(get).asString(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw storageFailure("객체 읽기 실패 key={}", key, e);
        }
    }

    /** 문자열을 객체로 저장한다(테스트 픽스처/디버깅용 경로). */
    public void writeUtf8(String key, String body, String contentType) {
        requireConfigured();
        try {
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(properties.s3().bucket())
                    .key(key)
                    .contentType(contentType)
                    .build();
            client().putObject(put, RequestBody.fromString(body, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw storageFailure("객체 저장 실패 key={}", key, e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.STORAGE_NOT_CONFIGURED);
        }
    }

    /** SDK 예외의 상세는 로그에만 남긴다(버킷명·키 등 내부 정보가 응답에 새지 않도록). */
    private BusinessException storageFailure(String message, String key, RuntimeException cause) {
        log.error("S3 " + message, key, cause);
        return new BusinessException(ErrorCode.STORAGE_FAILURE);
    }

    private S3Client client() {
        S3Client local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    // httpClientBuilder 로 넘겨 SDK 가 HTTP 클라이언트 생명주기까지 관리하게 한다
                    // (httpClient 로 완성된 인스턴스를 주면 close 시 함께 닫히지 않아 연결이 남는다).
                    local = S3Client.builder()
                            .region(Region.of(properties.region()))
                            .httpClientBuilder(UrlConnectionHttpClient.builder())
                            .build();
                    client = local;
                }
            }
        }
        return local;
    }

    private S3Presigner presigner() {
        S3Presigner local = presigner;
        if (local == null) {
            synchronized (this) {
                local = presigner;
                if (local == null) {
                    local = S3Presigner.builder()
                            .region(Region.of(properties.region()))
                            .build();
                    presigner = local;
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
        if (presigner != null) {
            presigner.close();
        }
    }
}
