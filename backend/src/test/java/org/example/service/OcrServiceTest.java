package org.example.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OcrServiceTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private final OcrService ocrService = new OcrService();

    @Test
    void 파일이_null이면_예외를_던진다() {
        assertThatThrownBy(() -> ocrService.extractText(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("업로드된 이미지가 없습니다");
    }

    @Test
    void 빈_파일이면_예외를_던진다() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> ocrService.extractText(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("업로드된 이미지가 없습니다");
    }

    @Test
    void 이미지가_아닌_파일이면_예외를_던진다() {
        MockMultipartFile notAnImage = new MockMultipartFile(
                "file", "note.txt", "text/plain", "이것은 이미지가 아닙니다".getBytes());

        assertThatThrownBy(() -> ocrService.extractText(notAnImage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 이미지 형식입니다");
    }

    @Test
    void 해상도가_지나치게_큰_이미지는_풀기_전에_거절한다() throws IOException {
        // 흰 바탕 1비트 PNG는 파일은 작지만 픽셀은 1억 개가 넘는다 (그대로 풀면 수백 MB)
        BufferedImage huge = new BufferedImage(10_001, 10_001, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(huge, "png", out);
        MockMultipartFile file = new MockMultipartFile("file", "huge.png", "image/png", out.toByteArray());

        assertThatThrownBy(() -> ocrService.extractText(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해상도가 너무 큽니다");
    }

    @Test
    void 텍스트가_선명한_이미지에서_실제_OCR로_문자열을_추출한다() throws IOException {
        OcrService realOcrService = new OcrService();
        ReflectionTestUtils.setField(realOcrService, "tessdataPath", "./tessdata");
        ReflectionTestUtils.setField(realOcrService, "language", "kor+eng");

        MockMultipartFile file = new MockMultipartFile(
                "file", "label.png", "image/png", renderTextImage("CLEANEAT"));

        String extracted = realOcrService.extractText(file);
        String lettersOnly = extracted.toUpperCase().replaceAll("[^A-Z]", "");

        assertThat(lettersOnly).contains("CLEANEAT");
    }

    @Test
    void 여러_요청이_동시에_OCR해도_각자_자기_이미지의_글자를_읽는다() throws Exception {
        OcrService realOcrService = new OcrService();
        ReflectionTestUtils.setField(realOcrService, "tessdataPath", "./tessdata");
        ReflectionTestUtils.setField(realOcrService, "language", "kor+eng");

        List<String> words = List.of("APPLE", "MANGO", "LEMON", "GRAPE");
        ExecutorService pool = Executors.newFixedThreadPool(words.size());
        try {
            List<Future<String>> results = new ArrayList<>();
            for (String word : words) {
                MockMultipartFile file = new MockMultipartFile("file", word + ".png", "image/png", renderTextImage(word));
                results.add(pool.submit(() -> realOcrService.extractText(file)));
            }
            for (int i = 0; i < words.size(); i++) {
                String lettersOnly = results.get(i).get(60, TimeUnit.SECONDS).toUpperCase().replaceAll("[^A-Z]", "");
                assertThat(lettersOnly).contains(words.get(i));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private byte[] renderTextImage(String text) throws IOException {
        BufferedImage image = new BufferedImage(500, 140, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 56));
        g.drawString(text, 20, 90);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
