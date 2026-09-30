package org.example.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.domain.Ingredient;
import org.example.domain.RiskLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 시드 사전(seed/harmful-ingredients.json)으로 찾기 규칙과 오탐 방지를 확인한다 */
class HarmfulIngredientMatcherTest {

    private record Seed(String name, String category, RiskLevel riskLevel, String description, List<String> aliases) {
    }

    private static List<Ingredient> dictionary;
    private static HarmfulIngredientMatcher matcher;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = new ClassPathResource("seed/harmful-ingredients.json").getInputStream()) {
            List<Seed> seeds = new ObjectMapper().readValue(in, new TypeReference<>() {});
            dictionary = seeds.stream()
                    .map(s -> Ingredient.builder().name(s.name()).category(s.category()).riskLevel(s.riskLevel())
                            .description(s.description()).aliases(new HashSet<>(s.aliases())).enabled(true).build())
                    .toList();
        }
        matcher = HarmfulIngredientMatcher.of(dictionary);
    }

    @Test
    void 시드_사전은_44종이고_이름이_겹치지_않으며_모두_설명과_분류가_있다() {
        assertThat(dictionary).hasSize(44);
        assertThat(dictionary).extracting(Ingredient::getName).doesNotHaveDuplicates();
        assertThat(dictionary).allSatisfy(i -> {
            assertThat(i.getDescription()).isNotBlank();
            assertThat(i.getCategory()).isNotBlank();
            assertThat(i.getAliases()).isNotEmpty();
        });
    }

    @Test
    void 여러_언어와_E번호_표기로_찾는다() {
        assertThat(matcher.find("Sodium Benzoate (E211)")).containsExactly("안식향산나트륨");
        assertThat(matcher.find("colorant : tartrazine")).containsExactly("황색4호");
        assertThat(matcher.find("sirop de glucose-fructose")).containsExactly("고과당옥수수시럽");
        assertThat(matcher.find("Emulsifier (E 433)")).containsExactly("폴리소르베이트");
        assertThat(matcher.find("antioxidant E-320")).containsExactly("부틸히드록시아니솔");
        assertThat(matcher.find("Flavour enhancer: Monosodium Glutamate")).containsExactly("L-글루타민산나트륨");
        assertThat(matcher.find("titanium dioxide")).containsExactly("이산화티타늄");
    }

    @Test
    void 한국어는_띄어쓰기와_제N호_표기를_모두_알아본다() {
        assertThat(matcher.find("식용색소황색제4호")).containsExactly("황색4호");
        assertThat(matcher.find("황색 제5호")).containsExactly("황색5호");
        assertThat(matcher.find("L-글루타민산나트륨(향미증진제)")).containsExactly("L-글루타민산나트륨");
        assertThat(matcher.find("부분경화유")).containsExactly("트랜스지방");
        assertThat(matcher.find("피로인산나트륨")).containsExactly("인산염");
    }

    @Test
    void 긴_이름_안의_짧은_이름은_따로_세지_않는다() {
        // 아질산나트륨 안에 질산나트륨, 안식향산나트륨 안에 안식향산
        assertThat(matcher.find("아질산나트륨")).containsExactly("아질산나트륨");
        assertThat(matcher.find("안식향산나트륨")).containsExactly("안식향산나트륨");
        assertThat(matcher.find("sodium nitrite")).containsExactly("아질산나트륨");
        // 따로 있으면 둘 다
        assertThat(matcher.find("아질산나트륨, 질산나트륨")).containsExactly("아질산나트륨", "질산나트륨");
    }

    @Test
    void 비슷한_번호나_단어_일부는_잘못_찾지_않는다() {
        assertThat(matcher.find("적색102호")).containsExactly("적색102호");
        assertThat(matcher.find("E1520")).isEmpty();
        assertThat(matcher.find("D&C Red 33")).isEmpty();
        assertThat(matcher.find("thymsg")).isEmpty();
        assertThat(matcher.find("정제수")).isEmpty();
        assertThat(matcher.find("caramel")).isEmpty();
        assertThat(matcher.find("e150a")).isEmpty();
    }

    @Test
    void 없다는_표시는_성분이_아니다() {
        assertThat(matcher.find("합성보존료 무첨가")).isEmpty();
        assertThat(matcher.find("sans conservateur")).isEmpty();
        assertThat(matcher.find("무MSG")).isEmpty();
        assertThat(matcher.find("MSG-free")).isEmpty();
    }

    @Test
    void 사용_중지한_성분은_찾지_않고_관리자가_추가한_성분은_바로_찾는다() {
        Ingredient disabled = Ingredient.builder().name("아스파탐").riskLevel(RiskLevel.MEDIUM)
                .aliases(Set.of("aspartame")).enabled(false).build();
        Ingredient added = Ingredient.builder().name("브롬산칼륨").riskLevel(RiskLevel.HIGH)
                .aliases(Set.of("potassium bromate", "e924")).build();

        HarmfulIngredientMatcher custom = HarmfulIngredientMatcher.of(List.of(disabled, added));

        assertThat(custom.find("aspartame")).isEmpty();
        assertThat(custom.find("Potassium Bromate")).containsExactly("브롬산칼륨");
        assertThat(custom.find("E924")).containsExactly("브롬산칼륨");
    }
}
