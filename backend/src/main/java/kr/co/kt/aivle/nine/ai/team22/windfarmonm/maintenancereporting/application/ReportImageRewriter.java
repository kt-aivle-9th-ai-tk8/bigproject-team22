package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port.ReportImagePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 보고서 본문(마크다운)의 {@code s3://} 이미지 링크를 presigned URL 로 바꾼다.
 * <p>
 * <b>저장된 본문은 건드리지 않고 응답에서만 바꾼다.</b> presigned URL 은 15분이면 만료되므로 DB 에 넣으면
 * 곧 죽은 링크가 박제된다. 반대로 마커({@code s3://버킷/키})는 영구히 유효하다.
 * <p>
 * 치환 대상은 마크다운 이미지 문법 {@code ![alt](s3://...)} 뿐이다. 일반 링크나 다른 스킴은 그대로 둔다 —
 * 본문에 우리가 모르는 외부 참조가 있어도 망가뜨리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ReportImageRewriter {

    /** {@code ![캡션](s3://버킷/키)} — 괄호를 포함하지 않는 URL 만 잡는다(마크다운 관례). */
    private static final Pattern S3_IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\((s3://[^)\\s]+)\\)");

    private final ReportImagePort imagePort;

    /**
     * 본문의 S3 이미지 마커를 서명된 URL 로 바꾼 사본을 돌려준다.
     * <p>
     * 같은 키가 여러 번 나와도 포트 호출은 한 번뿐이다(보고서 하나에 같은 사진이 반복 인용될 수 있다).
     * <b>실패(null)도 캐시한다</b> — {@code computeIfAbsent} 는 null 을 저장하지 않아, 그대로 쓰면
     * 서명할 수 없는 URI 가 반복될 때마다 매번 다시 시도하고 경고 로그도 그만큼 쌓인다.
     * <p>
     * 서명할 수 없는 링크(다른 버킷·저장소 미설정)는 <b>원문 그대로</b> 남긴다 — 조회를 실패시키는 대신
     * 그 이미지 한 장만 안 보이게 한다.
     */
    public String rewrite(String context) {
        if (context == null || !context.contains("s3://")) {
            return context;
        }
        Map<String, String> presignedByUri = new HashMap<>();
        Matcher matcher = S3_IMAGE.matcher(context);
        StringBuilder rewritten = new StringBuilder();
        while (matcher.find()) {
            String alt = matcher.group(1);
            String s3Uri = matcher.group(2);
            // computeIfAbsent 는 null 을 담지 않아 실패가 캐시되지 않는다 — containsKey 로 직접 다룬다.
            String url;
            if (presignedByUri.containsKey(s3Uri)) {
                url = presignedByUri.get(s3Uri);
            } else {
                url = imagePort.presignS3Uri(s3Uri);
                presignedByUri.put(s3Uri, url);
            }
            String replacement = url == null ? matcher.group() : "![" + alt + "](" + url + ")";
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }
}
