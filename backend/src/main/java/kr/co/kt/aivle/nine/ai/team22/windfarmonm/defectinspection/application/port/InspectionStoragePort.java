package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.application.port;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.PartSide;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 점검 이미지 저장소(S3) 포트. 키 규약({@code {prefix}/inspections/{id}/{bladeId}/{side}/{seq}.jpg})은
 * 어댑터 쪽 단일 지점(InspectionObjectKeys)이 소유하고, 애플리케이션은 논리 파라미터만 다룬다.
 * 저장소 미설정 시 구현이 {@code STORAGE_NOT_CONFIGURED}(503)를 던진다(앱은 정상, 해당 요청만 실패).
 */
public interface InspectionStoragePort {

    /** 이미지 1장 업로드용 presigned PUT URL 을 발급한다. */
    UploadTarget presignImageUpload(long inspectionId, long bladeId, PartSide partSide, int seq);

    /** 점검 프리픽스 아래에 <b>실제로 업로드된</b> 이미지들을 나열한다(S3 LIST — FE 신고가 아니라 원천 기준). */
    List<UploadedImage> listUploadedImages(long inspectionId);

    /** 이미지 키의 전체 S3 URI({@code s3://bucket/key}). SageMaker Async 의 InputLocation 으로 쓴다. */
    String imageS3Uri(String imageKey);

    /** 이미지 조회용 presigned GET URL(썸네일 링크). 인가는 호출측(담당 기반) 책임이다. */
    String presignImageView(String imageKey);

    /** S3 URI 가 가리키는 JSON 객체를 읽는다(추론 결과 outputLocation). */
    String readJson(String s3Uri);

    /**
     * 점검 1건에서 썸네일이 <b>없는</b> 원본마다 썸네일을 만든다(멱등 — 이미 있으면 건너뛴다).
     * 장당 실패는 건너뛰고 계속한다.
     *
     * @return 새로 만든 장수
     */
    int createMissingThumbnails(long inspectionId);

    /**
     * 주어진 점검들에서 <b>실재하는</b> 썸네일만 찾아 {@code 원본 키 → 썸네일 키} 로 돌려준다.
     * 조회 시 "썸네일이 있으면 그것을, 없으면 원본을" 고르는 데 쓴다 — 장마다 존재를 묻는 대신
     * 점검 프리픽스를 한 번씩 LIST 해서 판정한다.
     */
    Map<String, String> findThumbnailKeys(Collection<Long> inspectionIds);

    /** 발급된 업로드 대상(객체 키 + presigned URL). */
    record UploadTarget(String key, String url) {
    }

    /** 업로드된 이미지(키 + 키에서 해석한 블레이드/부위). */
    record UploadedImage(String key, long bladeId, PartSide partSide) {
    }
}
