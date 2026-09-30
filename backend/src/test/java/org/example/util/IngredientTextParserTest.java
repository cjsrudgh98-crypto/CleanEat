package org.example.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngredientTextParserTest {

    @Test
    void 쉼표로_구분된_성분을_분리한다() {
        List<String> result = IngredientTextParser.parse("밀가루, 설탕, 아스파탐");

        assertThat(result).containsExactly("밀가루", "설탕", "아스파탐");
    }

    @Test
    void 슬래시와_개행도_구분자로_처리한다() {
        List<String> result = IngredientTextParser.parse("밀가루/설탕\n아스파탐");

        assertThat(result).containsExactly("밀가루", "설탕", "아스파탐");
    }

    @Test
    void 토큰_앞뒤_공백을_제거한다() {
        List<String> result = IngredientTextParser.parse("  밀가루 ,  설탕  ");

        assertThat(result).containsExactly("밀가루", "설탕");
    }

    @Test
    void 빈_토큰은_결과에서_제외한다() {
        List<String> result = IngredientTextParser.parse("밀가루,,  ,설탕");

        assertThat(result).containsExactly("밀가루", "설탕");
    }

    @Test
    void null이나_빈_문자열은_빈_리스트를_반환한다() {
        assertThat(IngredientTextParser.parse(null)).isEmpty();
        assertThat(IngredientTextParser.parse("")).isEmpty();
        assertThat(IngredientTextParser.parse("   ")).isEmpty();
    }
}
