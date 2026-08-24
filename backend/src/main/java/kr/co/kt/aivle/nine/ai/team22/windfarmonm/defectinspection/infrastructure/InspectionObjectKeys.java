package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.domain.PartSide;

import java.util.Optional;

/**
 * 점검 이미지 S3 키 규약의 <b>단일 지점</b>: {@code {prefix}/inspections/{inspectionId}/{bladeId}/{partSide}/{seq}.jpg}.
 * 발급(조립)과 목록 해석(파싱)이 반드시 같은 규약을 쓰도록 이 클래스만이 형식을 안다.
 */
public final class InspectionObjectKeys {

    private static final String SEGMENT = "inspections";

    private InspectionObjectKeys() {
    }

    /** 이미지 객체 키를 만든다. */
    public static String imageKey(String prefix, long inspectionId, long bladeId, PartSide partSide, int seq) {
        return "%s/%s/%d/%d/%s/%d.jpg".formatted(prefix, SEGMENT, inspectionId, bladeId, partSide.name(), seq);
    }

    /** 썸네일 세그먼트. 원본과 같은 부위 폴더 아래 한 단계 더 들어간다. */
    public static final String THUMBNAIL_SEGMENT = "thumb";

    /**
     * 원본 키에서 썸네일 키를 유도한다: {@code .../{partSide}/{seq}.jpg} → {@code .../{partSide}/thumb/{seq}.jpg}.
     * <p>
     * 별도 루트가 아니라 부위 폴더 아래에 두는 이유는 점검 삭제·수명주기 정책에서 프리픽스 하나만 다루면
     * 되기 때문이다(두 곳으로 나누면 한쪽만 지웠을 때 고아 객체가 남는다). 세그먼트가 하나 늘어 5개가
     * 되므로 {@link #parse(String)} 가 썸네일을 <b>의도적으로</b> 걸러 낸다 — 추론 대상에 섞이지 않는다.
     */
    public static String thumbnailKey(String imageKey) {
        int lastSlash = imageKey.lastIndexOf('/');
        if (lastSlash < 0) {
            throw new IllegalArgumentException("규약 밖 이미지 키다: " + imageKey);
        }
        return imageKey.substring(0, lastSlash) + "/" + THUMBNAIL_SEGMENT + imageKey.substring(lastSlash);
    }

    /** 키가 썸네일인지. LIST 결과에서 원본과 파생물을 가르는 데 쓴다. */
    public static boolean isThumbnail(String key) {
        return key.contains("/" + THUMBNAIL_SEGMENT + "/");
    }

    /** 점검 1건의 모든 이미지가 놓이는 프리픽스(S3 LIST 용). 끝의 '/' 로 다른 점검 id 와의 접두 충돌을 막는다. */
    public static String inspectionPrefix(String prefix, long inspectionId) {
        return "%s/%s/%d/".formatted(prefix, SEGMENT, inspectionId);
    }

    /**
     * 키에서 (bladeId, partSide) 를 해석한다. 규약을 벗어난 키(수동 업로드 등)는 {@code empty} —
     * 호출측이 건너뛰고 로그로 남긴다.
     */
    public static Optional<ParsedImage> parse(String key) {
        int at = key.indexOf("/" + SEGMENT + "/");
        if (at < 0) {
            return Optional.empty();
        }
        // {inspectionId}/{bladeId}/{partSide}/{seq}.jpg — 세그먼트 수만이 아니라 전체 형식을 검증한다.
        // 느슨하면 규약 밖 객체(수동 업로드 등)가 추론 대상(아웃박스)으로 흘러든다.
        String[] rest = key.substring(at + SEGMENT.length() + 2).split("/");
        if (rest.length != 4 || !rest[3].matches("\\d+\\.jpg")) {
            return Optional.empty();
        }
        try {
            long inspectionId = Long.parseLong(rest[0]);
            long bladeId = Long.parseLong(rest[1]);
            PartSide partSide = PartSide.valueOf(rest[2]);
            if (inspectionId <= 0 || bladeId <= 0) {
                return Optional.empty();
            }
            return Optional.of(new ParsedImage(bladeId, partSide));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 키에서 해석된 이미지 속성. */
    public record ParsedImage(long bladeId, PartSide partSide) {
    }
}
