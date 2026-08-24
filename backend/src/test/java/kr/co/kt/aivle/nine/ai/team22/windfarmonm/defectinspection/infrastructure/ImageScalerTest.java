package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 썸네일 축소기. 드론 원본(수천만 픽셀)을 목록 표시 크기로 줄이는 것이 목적이라,
 * <b>결과 크기와 비율</b>이 계약이다.
 */
class ImageScalerTest {

    private static final int MAX_SIDE = 512;

    @Test
    @DisplayName("긴 변을 목표 크기로 맞추고 가로세로 비율을 유지한다")
    void scalesLongestSideKeepingRatio() {
        byte[] source = jpeg(4000, 3000); // 4:3

        byte[] thumbnail = ImageScaler.toThumbnailJpeg(source, MAX_SIDE);

        BufferedImage decoded = decode(thumbnail);
        assertThat(decoded.getWidth()).isEqualTo(MAX_SIDE);
        assertThat(decoded.getHeight()).isEqualTo(384); // 512 * 3/4
    }

    @Test
    @DisplayName("세로가 긴 이미지도 긴 변 기준으로 줄인다")
    void scalesPortraitByLongestSide() {
        BufferedImage decoded = decode(ImageScaler.toThumbnailJpeg(jpeg(1500, 3000), MAX_SIDE));

        assertThat(decoded.getHeight()).isEqualTo(MAX_SIDE);
        assertThat(decoded.getWidth()).isEqualTo(256);
    }

    @Test
    @DisplayName("이미 작은 이미지는 키우지 않는다 — 원본 크기를 그대로 둔다")
    void doesNotUpscale() {
        BufferedImage decoded = decode(ImageScaler.toThumbnailJpeg(jpeg(300, 200), MAX_SIDE));

        assertThat(decoded.getWidth()).isEqualTo(300);
        assertThat(decoded.getHeight()).isEqualTo(200);
    }

    @Test
    @DisplayName("결과는 원본보다 확실히 작다 — 목록 로드량을 줄이는 것이 존재 이유다")
    void thumbnailIsSmallerThanSource() {
        byte[] source = jpeg(4000, 3000);

        byte[] thumbnail = ImageScaler.toThumbnailJpeg(source, MAX_SIDE);

        assertThat(thumbnail.length).isLessThan(source.length);
    }

    @Test
    @DisplayName("이미지가 아닌 바이트는 예외로 알린다 — 조용히 빈 썸네일을 만들지 않는다")
    void rejectsNonImageBytes() {
        assertThatThrownBy(() -> ImageScaler.toThumbnailJpeg("not an image".getBytes(), MAX_SIDE))
                .isInstanceOf(UncheckedIOException.class);
    }

    /** 균일 단색이면 JPEG 이 과하게 압축되어 크기 비교가 무의미해지므로 무늬를 넣는다. */
    private static byte[] jpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int x = 0; x < width; x += 16) {
                graphics.setColor(new Color((x * 7) % 256, (x * 13) % 256, (x * 29) % 256));
                graphics.fillRect(x, 0, 16, height);
            }
        } finally {
            graphics.dispose();
        }
        return encode(image);
    }

    private static byte[] encode(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
