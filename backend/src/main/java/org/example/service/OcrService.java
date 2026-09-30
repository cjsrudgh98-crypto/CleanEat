package org.example.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.example.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

@Service
public class OcrService {

    // Tesseract 페이지 분할 모드 6: "하나의 균일한 글 덩어리" - 영수증처럼 왼쪽 상품명과 오른쪽 끝 가격이
    // 멀리 떨어진 줄도 한 줄로 읽는다 (모드 4는 오른쪽 가격 칸을 줄에서 떼어내는 경우가 있었음)
    private static final int PSM_UNIFORM_BLOCK = 6;
    // 이보다 좁은 사진은 키워서 읽는다 (영수증 글자가 작아서 원본 그대로면 인식률이 크게 떨어짐)
    private static final int MIN_RECEIPT_WIDTH = 1400;
    // 이보다 픽셀이 많은 이미지는 거절한다 (압축 폭탄 방지 - 1억 화소)
    static final long MAX_SOURCE_PIXELS = 100_000_000L;
    // 이보다 크면 줄여서 읽는다 - OCR에는 이 정도(약 4600x3450)면 충분하고, 메모리는 최대 약 64MB로 묶인다
    static final long MAX_DECODE_PIXELS = 16_000_000L;

    @Value("${app.ocr.tessdata-path}")
    private String tessdataPath;

    @Value("${app.ocr.language}")
    private String language;

    public String extractText(MultipartFile file) {
        BufferedImage image = readImage(file);
        try {
            return newTesseract().doOCR(image);
        } catch (TesseractException e) {
            throw new ServiceUnavailableException("이미지에서 글자를 읽지 못했습니다. 잠시 후 다시 시도해주세요", e);
        }
    }

    /** 영수증 전용: 흑백 + 확대 후, 줄 단위로 읽는 설정으로 OCR */
    public String extractReceiptText(MultipartFile file) {
        BufferedImage image = prepareReceipt(readImage(file));
        Tesseract receiptTesseract = newTesseract();
        receiptTesseract.setPageSegMode(PSM_UNIFORM_BLOCK);
        try {
            return receiptTesseract.doOCR(image);
        } catch (TesseractException e) {
            throw new ServiceUnavailableException("이미지에서 글자를 읽지 못했습니다. 잠시 후 다시 시도해주세요", e);
        }
    }

    // Tesseract 객체는 여러 스레드가 함께 쓰면 안 된다 (doOCR마다 내부 네이티브 핸들을 만들고 지우므로,
    // 동시에 들어온 요청끼리 결과가 섞이거나 네이티브 크래시가 날 수 있음) - 요청마다 새로 만든다 (생성 비용은 작음)
    private Tesseract newTesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(language);
        return tesseract;
    }

    private BufferedImage readImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 이미지가 없습니다");
        }
        // 파일 용량(10MB)만으로는 부족하다 - 압축이 잘 되는 PNG는 작은 파일로도 수만x수만 픽셀이 될 수 있어서
        // 그대로 풀면 메모리가 바닥난다. 픽셀 수를 먼저 확인하고, 크면 줄여서(건너뛰며) 읽는다
        try (ImageInputStream in = ImageIO.createImageInputStream(file.getInputStream())) {
            Iterator<ImageReader> readers = in != null ? ImageIO.getImageReaders(in) : Collections.emptyIterator();
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_SOURCE_PIXELS) {
                    throw new IllegalArgumentException("이미지 해상도가 너무 큽니다. 사진 크기를 줄여서 다시 올려주세요");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                if (pixels > MAX_DECODE_PIXELS) {
                    int step = (int) Math.ceil(Math.sqrt((double) pixels / MAX_DECODE_PIXELS));
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지를 읽을 수 없습니다", e);
        }
    }

    private static BufferedImage prepareReceipt(BufferedImage source) {
        double scale = source.getWidth() < MIN_RECEIPT_WIDTH ? (double) MIN_RECEIPT_WIDTH / source.getWidth() : 1.0;
        // 아주 좁고 긴 이미지를 키우면 픽셀 수가 폭발하므로, 키운 결과도 MAX_DECODE_PIXELS를 넘지 않게 한다
        double pixels = (double) source.getWidth() * source.getHeight();
        scale = Math.min(scale, Math.max(1.0, Math.sqrt(MAX_DECODE_PIXELS / pixels)));
        int width = (int) Math.round(source.getWidth() * scale);
        int height = (int) Math.round(source.getHeight() * scale);
        BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return gray;
    }
}
