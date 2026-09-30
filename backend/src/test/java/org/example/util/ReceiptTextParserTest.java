package org.example.util;

import org.example.util.ReceiptTextParser.ReceiptLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptTextParserTest {

    private static final String RECEIPT = """
            CleanEat 마트 강남점
            사업자번호 123-45-67890 대표 홍길동
            TEL 02-1234-5678
            2026-09-29 15:32  POS 01
            ------------------------------
            상품명          수량    금액
            001 무염구운아몬드      1   6,900
            8800000000059
            002 유기농두부         2   6,600
            *Nutella 200g        1   4,500
            ------------------------------
            합계                     18,000
            부가세                    1,636
            카드결제                 18,000
            승인번호 12345678
            감사합니다
            """;

    @Test
    void 머리말_꼬리말은_버리고_상품줄만_골라낸다() {
        ReceiptTextParser.Parsed parsed = ReceiptTextParser.parse(RECEIPT);

        assertThat(parsed.lines()).extracting(ReceiptLine::name)
                .containsExactly("무염구운아몬드", "유기농두부", "Nutella 200g");
    }

    @Test
    void 줄_끝의_수량을_읽는다() {
        ReceiptTextParser.Parsed parsed = ReceiptTextParser.parse(RECEIPT);

        assertThat(parsed.lines()).extracting(ReceiptLine::quantity).containsExactly(1, 2, 1);
    }

    @Test
    void 영수증에_찍힌_상품_바코드를_모은다() {
        ReceiptTextParser.Parsed parsed = ReceiptTextParser.parse(RECEIPT);

        // 승인번호(8자리)도 숫자라 후보로 들어오지만, 실제 사용 여부는 ScanService가 검증 숫자/DB로 거른다
        assertThat(parsed.barcodes()).contains("8800000000059");
    }

    @Test
    void 가격이_아랫줄에_찍히는_두줄_영수증도_읽는다() {
        ReceiptTextParser.Parsed parsed = ReceiptTextParser.parse("""
                GS25 역삼점
                유기농 두부
                8800000008058  3   9,900
                무가당 두유
                1,800
                """);

        assertThat(parsed.lines()).extracting(ReceiptLine::name).containsExactly("유기농 두부", "무가당 두유");
        assertThat(parsed.lines()).extracting(ReceiptLine::quantity).containsExactly(3, 1);
    }

    @Test
    void OCR이_가격칸을_놓쳐도_수량이_있으면_상품줄로_본다() {
        ReceiptTextParser.Parsed parsed = ReceiptTextParser.parse("""
                그린마트 역삼점
                001 무염구운아몬드             1
                002 유기농두부                   2
                """);

        assertThat(parsed.lines()).extracting(ReceiptLine::name).containsExactly("무염구운아몬드", "유기농두부");
        assertThat(parsed.lines()).extracting(ReceiptLine::quantity).containsExactly(1, 2);
    }

    @Test
    void 글자가_없으면_빈_결과다() {
        assertThat(ReceiptTextParser.parse("").lines()).isEmpty();
        assertThat(ReceiptTextParser.parse("12,000\n---\n").lines()).isEmpty();
    }
}
