package org.example.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ProductNameMatcherTest {

    private static final List<String> CATALOG = List.of(
            "무염 구운 아몬드", "유기농 두부", "두부 채소 비빔밥", "하루 견과 믹스 (20봉)", "Nutella", "무가당 두유");

    private String best(String receiptName) {
        return ProductNameMatcher.best(receiptName, CATALOG, Function.identity())
                .map(ProductNameMatcher.Match::item).orElse(null);
    }

    @Test
    void 공백없이_찍힌_이름을_찾는다() {
        assertThat(best("무염구운아몬드")).isEqualTo("무염 구운 아몬드");
    }

    @Test
    void 영수증에서_잘린_이름을_찾는다() {
        assertThat(best("무염구운아몬")).isEqualTo("무염 구운 아몬드");
        assertThat(best("하루견과믹스")).isEqualTo("하루 견과 믹스 (20봉)");
    }

    @Test
    void OCR이_한_글자_틀려도_찾는다() {
        assertThat(best("무염구문아몬드")).isEqualTo("무염 구운 아몬드");
    }

    @Test
    void 짧은_이름은_길이가_더_비슷한_상품을_고른다() {
        assertThat(best("유기농두부")).isEqualTo("유기농 두부");
        assertThat(best("두부")).isEqualTo("유기농 두부");
    }

    @Test
    void 용량_표기가_붙어도_찾는다() {
        assertThat(best("Nutella 200g")).isEqualTo("Nutella");
    }

    @Test
    void 비슷한_상품이_없으면_찾지_않는다() {
        assertThat(best("삼겹살")).isNull();
        assertThat(best("가")).isNull();
    }
}
