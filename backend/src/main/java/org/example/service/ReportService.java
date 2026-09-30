package org.example.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.annotation.PostConstruct;
import org.example.domain.RiskLevel;
import org.example.domain.ScanHistory;
import org.example.util.IngredientTextParser;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String KOREAN_RISK_LOW = "안전";
    private static final String KOREAN_RISK_MEDIUM = "주의";
    private static final String KOREAN_RISK_HIGH = "위험";

    private BaseFont koreanBaseFont;

    // PDF에 넣는 한글 폰트 - 나눔고딕 (SIL Open Font License, 재배포 허용 - 라이선스 전문은 fonts/OFL.txt).
    // 윈도우 시스템 폰트(맑은 고딕 등)는 재배포가 허용되지 않아서 저장소/이미지에 넣으면 안 된다
    static final String FONT_FILE = "NanumGothic-Regular.ttf";

    @PostConstruct
    public void init() throws IOException, com.lowagie.text.DocumentException {
        try (InputStream is = new ClassPathResource("fonts/" + FONT_FILE).getInputStream()) {
            byte[] fontBytes = is.readAllBytes();
            koreanBaseFont = BaseFont.createFont(FONT_FILE, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    true, fontBytes, null);
        }
    }

    public byte[] generateScanReport(ScanHistory history) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter.getInstance(document, out);
            document.open();

            BaseFont baseFont = koreanBaseFont;
            Font titleFont = new Font(baseFont, 18, Font.BOLD);
            Font headingFont = new Font(baseFont, 13, Font.BOLD);
            Font bodyFont = new Font(baseFont, 11, Font.NORMAL);

            document.add(new Paragraph("CleanEat 성분 분석 상세 리포트", titleFont));
            document.add(spacer());

            String productName = history.getProduct() != null ? history.getProduct().getName() : "OCR 스캔";
            String barcode = history.getProduct() != null ? history.getProduct().getBarcode() : "-";

            document.add(paragraph("제품명: " + productName, bodyFont));
            document.add(paragraph("바코드: " + barcode, bodyFont));
            document.add(paragraph("스캔 일시: " + history.getScannedAt().format(DATE_FORMAT), bodyFont));
            document.add(paragraph("종합 위험도: " + riskLabel(history.getOverallRisk()), headingFont));
            document.add(spacer());

            document.add(new Paragraph("유해 성분", headingFont));
            document.add(bulletedList(IngredientTextParser.parse(history.getMatchedHarmfulIngredients()), bodyFont, "발견된 유해 성분이 없습니다."));
            document.add(spacer());

            document.add(new Paragraph("알레르기 유발 성분", headingFont));
            document.add(bulletedList(IngredientTextParser.parse(history.getMatchedAllergens()), bodyFont, "발견된 알레르기 유발 성분이 없습니다."));
            document.add(spacer());

            if (history.getRawIngredientsText() != null && !history.getRawIngredientsText().isBlank()) {
                document.add(new Paragraph("전체 성분표", headingFont));
                document.add(paragraph(history.getRawIngredientsText(), bodyFont));
            }

            document.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF 리포트 생성 중 오류가 발생했습니다", e);
        } catch (com.lowagie.text.DocumentException e) {
            throw new IllegalStateException("PDF 문서 생성 중 오류가 발생했습니다", e);
        }
    }

    private Paragraph paragraph(String text, Font font) {
        return new Paragraph(text, font);
    }

    private Paragraph spacer() {
        return new Paragraph(" ");
    }

    private com.lowagie.text.List bulletedList(List<String> items, Font font, String emptyMessage) {
        com.lowagie.text.List list = new com.lowagie.text.List(com.lowagie.text.List.UNORDERED);
        if (items.isEmpty()) {
            list.add(new com.lowagie.text.ListItem(emptyMessage, font));
            return list;
        }
        for (String item : items) {
            list.add(new com.lowagie.text.ListItem(item, font));
        }
        return list;
    }

    private String riskLabel(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> KOREAN_RISK_LOW;
            case MEDIUM -> KOREAN_RISK_MEDIUM;
            case HIGH -> KOREAN_RISK_HIGH;
        };
    }
}
