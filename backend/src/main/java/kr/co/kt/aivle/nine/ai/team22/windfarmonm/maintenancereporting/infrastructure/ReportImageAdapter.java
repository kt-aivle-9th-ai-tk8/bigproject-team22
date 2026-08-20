package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws.AwsProperties;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws.S3ObjectStorage;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port.ReportImagePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@link ReportImagePort} 어댑터. {@code s3://버킷/키} 를 해석해 presigned GET URL 로 바꾼다.
 * <p>
 * <b>다른 버킷은 건드리지 않는다.</b> 우리 데이터 버킷이 아닌 링크는 외부 이미지이거나 설정 어긋남이라,
 * 임의로 우리 버킷에 있다고 가정하고 서명하면 열리지 않는 URL 만 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AwsProperties.class)
public class ReportImageAdapter implements ReportImagePort {

    private static final String SCHEME = "s3://";

    private final S3ObjectStorage storage;
    private final AwsProperties properties;

    @Override
    public String presignS3Uri(String s3Uri) {
        String bucket = properties.s3().bucket();
        if (bucket == null || bucket.isBlank()) {
            return null; // 저장소 미설정 — 보고서는 그대로 열리고 이미지만 비어 보인다
        }
        String expectedPrefix = SCHEME + bucket + "/";
        if (s3Uri == null || !s3Uri.startsWith(expectedPrefix)) {
            return null; // 우리 버킷이 아니다
        }
        String key = s3Uri.substring(expectedPrefix.length());
        if (key.isBlank()) {
            return null;
        }
        try {
            return storage.presignGet(key);
        } catch (RuntimeException e) {
            // 서명 실패가 보고서 조회 전체를 깨서는 안 된다. 그 이미지 한 장만 마커로 남는다.
            log.warn("보고서 본문 이미지 서명 실패 — 원문을 유지한다 uri={}", s3Uri, e);
            return null;
        }
    }
}
