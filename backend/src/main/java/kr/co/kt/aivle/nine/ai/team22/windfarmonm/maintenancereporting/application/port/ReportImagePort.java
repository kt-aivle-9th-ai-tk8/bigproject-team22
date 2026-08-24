package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port;

/**
 * 보고서 본문에 박힌 S3 이미지 마커를 열람 가능한 URL 로 바꾸는 포트.
 * <p>
 * report-agent 는 이미지 링크에 {@code IMAGE_BASE_URL} 을 접두로 붙이는데, 그 값을 {@code s3://버킷} 으로
 * 두기로 했다. 그러면 본문에는 <b>공개 URL 이 아니라 S3 키</b>가 남고(안정 마커), 열람 시점에 BE 가
 * 담당 인가를 통과한 요청에만 서명해 준다 — 인가 입자가 결함 이미지 조회와 같아진다.
 */
public interface ReportImagePort {

    /**
     * {@code s3://버킷/키} 를 presigned GET URL 로 바꾼다.
     *
     * @return 서명된 URL. 우리 버킷이 아니거나 저장소가 설정되지 않아 서명할 수 없으면 {@code null}
     *         (호출측이 원문을 그대로 두고 넘어간다 — 보고서 조회 자체가 실패해서는 안 된다)
     */
    String presignS3Uri(String s3Uri);
}
