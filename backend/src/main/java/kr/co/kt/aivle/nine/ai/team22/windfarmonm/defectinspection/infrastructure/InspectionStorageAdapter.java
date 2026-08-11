package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port.InspectionStoragePort;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.PartSide;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws.AwsProperties;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.aws.S3ObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@link InspectionStoragePort} 어댑터. 키 규약({@link InspectionObjectKeys})을 소유하고
 * 실제 S3 접근은 {@link S3ObjectStorage} 에 위임한다(미설정 시 그쪽에서 503).
 * <p>
 * 업로드 콘텐츠 타입은 {@code image/jpeg} 로 고정한다 — 드론 촬영 원본이 JPEG 이고,
 * DL 추론 입력({@code input_fn})도 jpeg/png 만 받는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AwsProperties.class)
public class InspectionStorageAdapter implements InspectionStoragePort {

    private static final String IMAGE_CONTENT_TYPE = "image/jpeg";

    private final S3ObjectStorage storage;
    private final AwsProperties properties;

    @Override
    public UploadTarget presignImageUpload(long inspectionId, long bladeId, PartSide partSide, int seq) {
        String key = InspectionObjectKeys.imageKey(prefix(), inspectionId, bladeId, partSide, seq);
        return new UploadTarget(key, storage.presignPut(key, IMAGE_CONTENT_TYPE));
    }

    @Override
    public List<UploadedImage> listUploadedImages(long inspectionId) {
        String listPrefix = InspectionObjectKeys.inspectionPrefix(prefix(), inspectionId);
        return storage.listKeys(listPrefix).stream()
                .map(key -> {
                    Optional<InspectionObjectKeys.ParsedImage> parsed = InspectionObjectKeys.parse(key);
                    if (parsed.isEmpty()) {
                        // 규약 밖 객체(수동 업로드 등)는 추론 대상에서 제외하고 흔적만 남긴다.
                        log.warn("점검 {} 프리픽스에 규약 밖 객체가 있다 — 건너뜀: {}", inspectionId, key);
                        return null;
                    }
                    return new UploadedImage(key, parsed.get().bladeId(), parsed.get().partSide());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private String prefix() {
        return properties.s3().prefix();
    }
}
