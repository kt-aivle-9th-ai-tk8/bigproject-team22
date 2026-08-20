package kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.maintenancereporting.application.port.ReportImagePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 보고서 본문의 S3 이미지 마커 치환. 저장본에는 영구 마커를, 응답에만 만료되는 서명 URL 을 둔다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportImageRewriterTest {

    @Mock
    ReportImagePort imagePort;
    @InjectMocks
    ReportImageRewriter rewriter;

    @Test
    @DisplayName("s3:// 이미지 링크를 presigned URL 로 바꾸고 캡션은 보존한다")
    void rewritesS3ImageLinks() {
        given(imagePort.presignS3Uri("s3://bucket/content/inspections/5/31/LE/1.jpg"))
                .willReturn("https://signed/1");

        String result = rewriter.rewrite(
                "본문\n\n![U1 · A블레이드 · 앞전(LE) · 2번째 사진](s3://bucket/content/inspections/5/31/LE/1.jpg)\n");

        assertThat(result).contains("![U1 · A블레이드 · 앞전(LE) · 2번째 사진](https://signed/1)");
        assertThat(result).doesNotContain("s3://");
    }

    @Test
    @DisplayName("같은 이미지가 여러 번 인용돼도 서명은 한 번만 한다")
    void signsEachUriOnce() {
        given(imagePort.presignS3Uri("s3://bucket/a.jpg")).willReturn("https://signed/a");

        String result = rewriter.rewrite("![1](s3://bucket/a.jpg) 그리고 ![2](s3://bucket/a.jpg)");

        verify(imagePort, times(1)).presignS3Uri("s3://bucket/a.jpg");
        assertThat(result).isEqualTo("![1](https://signed/a) 그리고 ![2](https://signed/a)");
    }

    @Test
    @DisplayName("서명할 수 없는 링크는 원문을 그대로 둔다 — 보고서 조회를 실패시키지 않는다")
    void keepsUnsignableLinksAsIs() {
        given(imagePort.presignS3Uri("s3://other-bucket/a.jpg")).willReturn(null);

        String result = rewriter.rewrite("![x](s3://other-bucket/a.jpg)");

        assertThat(result).isEqualTo("![x](s3://other-bucket/a.jpg)");
    }

    @Test
    @DisplayName("서명에 실패한 URI 가 반복돼도 다시 시도하지 않는다 — 실패도 캐시한다")
    void cachesFailedSignatureToo() {
        given(imagePort.presignS3Uri("s3://other-bucket/a.jpg")).willReturn(null);

        String result = rewriter.rewrite("![1](s3://other-bucket/a.jpg) ![2](s3://other-bucket/a.jpg)");

        // 캐시하지 않으면 반복 횟수만큼 서명을 시도하고 경고 로그도 그만큼 쌓인다.
        verify(imagePort, times(1)).presignS3Uri("s3://other-bucket/a.jpg");
        assertThat(result).isEqualTo("![1](s3://other-bucket/a.jpg) ![2](s3://other-bucket/a.jpg)");
    }

    @Test
    @DisplayName("이미지가 아닌 링크와 다른 스킴은 건드리지 않는다")
    void leavesNonImageAndOtherSchemesAlone() {
        String source = "[문서](s3://bucket/a.pdf) 와 ![외부](https://cdn.example/a.jpg)";

        assertThat(rewriter.rewrite(source)).isEqualTo(source);
    }

    @Test
    @DisplayName("본문이 없거나 마커가 없으면 그대로 반환한다(불필요한 순회 없음)")
    void returnsUnchangedWhenNoMarker() {
        assertThat(rewriter.rewrite(null)).isNull();
        assertThat(rewriter.rewrite("결함 없음")).isEqualTo("결함 없음");
    }
}
