package kr.co.kt.aivle.nine.ai.team22.windfarmonm.defectinspection.infrastructure;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

/**
 * 원본 이미지를 표시용 썸네일 JPEG 으로 줄인다.
 * <p>
 * <b>서브샘플링 디코딩</b>이 이 클래스의 핵심이다. 드론 원본(8256×5504)을 통째로 디코딩하면 RGBA 기준
 * 약 180MB 인데, {@link ImageReadParam#setSourceSubsampling} 으로 n 픽셀마다 하나씩만 읽으면 그 비율의
 * 제곱만큼 줄어든다(4배 서브샘플링이면 약 11MB). 이 덕분에 Lambda 없이 애플리케이션에서 처리할 수 있다.
 * <p>
 * 서브샘플링 배수는 2의 거듭제곱으로만 잡는다 — JPEG 디코더가 그 배수에서 DCT 스케일링으로 실제 작업량을
 * 줄이기 때문이다. 정확한 크기는 그 뒤 한 번 더 축소해 맞춘다(서브샘플링만으로는 배수 단위로만 줄어든다).
 */
final class ImageScaler {

    /** 서브샘플링 후에도 목표보다 이만큼은 커야 한다 — 너무 일찍 줄이면 최종 축소가 뭉갠다. */
    private static final int QUALITY_HEADROOM = 2;

    /** 서브샘플링 상한. 원본이 아무리 커도 이 이상은 건너뛰지 않는다(과도한 정보 손실 방지). */
    private static final int MAX_SUBSAMPLING = 16;

    private ImageScaler() {
    }

    /**
     * 긴 변이 {@code maxSide} 이하가 되도록 줄인 JPEG 바이트를 만든다. 원본이 이미 작으면 비율은 유지한 채
     * 다시 인코딩만 한다(호출측이 크기를 따지지 않아도 되게).
     *
     * @throws UncheckedIOException 디코딩·인코딩 실패(손상 파일, 미지원 포맷)
     */
    static byte[] toThumbnailJpeg(byte[] source, int maxSide) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new UncheckedIOException(new IOException("읽을 수 있는 이미지 포맷이 아니다"));
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                BufferedImage decoded = decodeSubsampled(reader, maxSide);
                BufferedImage thumbnail = scaleToMaxSide(decoded, maxSide);
                return encodeJpeg(thumbnail);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage decodeSubsampled(ImageReader reader, int maxSide) throws IOException {
        int longestSide = Math.max(reader.getWidth(0), reader.getHeight(0));
        int step = subsamplingStep(longestSide, maxSide);
        ImageReadParam param = reader.getDefaultReadParam();
        if (step > 1) {
            param.setSourceSubsampling(step, step, 0, 0);
        }
        return reader.read(0, param);
    }

    /** 목표의 {@link #QUALITY_HEADROOM} 배는 남기는 가장 큰 2의 거듭제곱. */
    private static int subsamplingStep(int longestSide, int maxSide) {
        int step = 1;
        while (step * 2 <= MAX_SUBSAMPLING && longestSide / (step * 2) >= maxSide * QUALITY_HEADROOM) {
            step *= 2;
        }
        return step;
    }

    private static BufferedImage scaleToMaxSide(BufferedImage source, int maxSide) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longestSide = Math.max(width, height);
        double ratio = longestSide <= maxSide ? 1.0 : (double) maxSide / longestSide;
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));

        // JPEG 은 알파를 담지 못한다 — TYPE_INT_RGB 로 그려 투명 픽셀이 검게 뭉치는 것을 막는다.
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpeg", out)) {
            throw new IOException("JPEG 인코더를 찾지 못했다");
        }
        return out.toByteArray();
    }
}
