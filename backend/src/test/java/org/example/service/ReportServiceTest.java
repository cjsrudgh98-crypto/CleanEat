package org.example.service;

import org.example.domain.Product;
import org.example.domain.RiskLevel;
import org.example.domain.ScanHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReportServiceTest {

    private final ReportService reportService = new ReportService();

    @BeforeEach
    void setUp() throws Exception {
        reportService.init();
    }

    @Test
    void 스캔기록으로_PDF_리포트를_생성한다() {
        Product product = Product.builder().id(1L).name("테스트 과자").barcode("8801234567890").build();
        ScanHistory history = ScanHistory.builder()
                .id(1L)
                .product(product)
                .overallRisk(RiskLevel.MEDIUM)
                .matchedHarmfulIngredients("아스파탐")
                .matchedAllergens("밀, 계란")
                .rawIngredientsText("밀가루, 아스파탐, 계란")
                .scannedAt(LocalDateTime.now())
                .build();

        byte[] pdf = reportService.generateScanReport(history);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void 한글이_깨지지_않고_나눔고딕이_포함된다() throws Exception {
        Product product = Product.builder().id(1L).name("무첨가 현미 과자").barcode("8801234567890").build();
        ScanHistory history = ScanHistory.builder()
                .id(3L).product(product).overallRisk(RiskLevel.MEDIUM)
                .matchedHarmfulIngredients("아스파탐").matchedAllergens("밀")
                .rawIngredientsText("현미, 아스파탐").scannedAt(LocalDateTime.now())
                .build();

        byte[] pdf = reportService.generateScanReport(history);

        // PDF 안의 글자를 다시 꺼내서 한글이 그대로인지 (폰트에 글자가 없으면 빈칸/깨진 글자로 나온다)
        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdf);
        String text = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(text).contains("무첨가 현미 과자", "아스파탐", "주의", "성분 분석 상세 리포트");
        // 재배포가 허용된 나눔고딕이 문서에 들어가 있어야 한다 (받는 사람 PC에 폰트가 없어도 보이게)
        assertThat(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("NanumGothic");
        reader.close();
    }

    @Test
    void 유해성분이_없어도_PDF를_생성한다() {
        ScanHistory history = ScanHistory.builder()
                .id(2L)
                .product(null)
                .overallRisk(RiskLevel.LOW)
                .matchedHarmfulIngredients("")
                .matchedAllergens("")
                .rawIngredientsText("정제수, 정제소금")
                .scannedAt(LocalDateTime.now())
                .build();

        byte[] pdf = reportService.generateScanReport(history);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
