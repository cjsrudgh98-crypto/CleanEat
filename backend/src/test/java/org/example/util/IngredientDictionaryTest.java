package org.example.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IngredientDictionaryTest {

    // 유해성분 찾기는 DB 사전 기반 HarmfulIngredientMatcher로 옮겼다 (HarmfulIngredientMatcherTest)

    @Test
    void 알레르기를_여러_언어로_찾는다() {
        assertThat(IngredientDictionary.findAllergens("lait écrémé en poudre")).containsExactly("우유");
        assertThat(IngredientDictionary.findAllergens("Whey powder (milk)")).containsExactly("우유");
        assertThat(IngredientDictionary.findAllergens("soy lecithin")).containsExactly("대두");
        assertThat(IngredientDictionary.findAllergens("탈지분유")).containsExactly("우유");
    }

    @Test
    void 없다는_표시는_알레르기로_치지_않는다() {
        // 누텔라 성분표 끝의 "Sans gluten" -> 밀로 잡히면 안 됨
        assertThat(IngredientDictionary.findAllergens("vanilline. Sans gluten.")).isEmpty();
        assertThat(IngredientDictionary.findAllergens("gluten-free")).isEmpty();
        assertThat(IngredientDictionary.findAllergens("우유 무첨가")).isEmpty();
        assertThat(IngredientDictionary.findAllergens("글루텐프리")).isEmpty();
        // "프리미엄"은 없다는 뜻이 아님
        assertThat(IngredientDictionary.findAllergens("프리미엄 우유")).containsExactly("우유");
    }

    @Test
    void 한글자_알레르기는_단어_앞에_올때만_인정한다() {
        assertThat(IngredientDictionary.findAllergens("밀(국산)")).containsExactly("밀");
        assertThat(IngredientDictionary.findAllergens("게살")).containsExactly("게");
        // "밀크"는 밀이 아니라 우유
        assertThat(IngredientDictionary.findAllergens("밀크초콜릿")).containsExactly("우유");
        // 단어 중간의 "게"는 무시
        assertThat(IngredientDictionary.findAllergens("소금게")).isEmpty();
    }
}
