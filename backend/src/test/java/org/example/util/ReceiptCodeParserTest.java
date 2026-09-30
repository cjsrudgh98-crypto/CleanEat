package org.example.util;

import org.example.util.ReceiptCodeParser.BarcodeList;
import org.example.util.ReceiptCodeParser.OrderReceipt;
import org.example.util.ReceiptCodeParser.Unknown;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptCodeParserTest {

    private static final String ORDER = "CE-0123456789abcdef0123456789abcdef";

    @Test
    void CleanEat_영수증_코드에서_주문번호를_찾는다() {
        assertThat(ReceiptCodeParser.parse("CLEANEAT-RECEIPT:" + ORDER)).isEqualTo(new OrderReceipt(ORDER));
        // URL 등 다른 글자에 섞여 있어도 찾는다
        assertThat(ReceiptCodeParser.parse("https://cleaneat.example/receipt?code=" + ORDER))
                .isEqualTo(new OrderReceipt(ORDER));
    }

    @Test
    void 바코드_목록과_수량을_읽는다() {
        ReceiptCodeParser.Parsed parsed = ReceiptCodeParser.parse("3017620422003*2\n5449000000996 x3, 3017620422003");

        assertThat(parsed).isInstanceOf(BarcodeList.class);
        // 같은 바코드는 수량을 합치고, 처음 나온 순서를 유지한다
        assertThat(((BarcodeList) parsed).quantities())
                .containsExactly(Map.entry("3017620422003", 3), Map.entry("5449000000996", 3));
    }

    @Test
    void 바코드가_없으면_알수없는_코드다() {
        assertThat(ReceiptCodeParser.parse("영수증 번호 1234")).isInstanceOf(Unknown.class);
        assertThat(ReceiptCodeParser.parse("  ")).isInstanceOf(Unknown.class);
    }

    @Test
    void 바코드_검증숫자를_확인한다() {
        assertThat(ReceiptCodeParser.isValidGtin("3017620422003")).isTrue();  // EAN-13
        assertThat(ReceiptCodeParser.isValidGtin("5449000000996")).isTrue();
        assertThat(ReceiptCodeParser.isValidGtin("036000291452")).isTrue();   // UPC-A
        assertThat(ReceiptCodeParser.isValidGtin("96385074")).isTrue();       // EAN-8
        assertThat(ReceiptCodeParser.isValidGtin("3017620422004")).isFalse();
        assertThat(ReceiptCodeParser.isValidGtin("01012345678")).isFalse();   // 11자리 전화번호
    }
}
