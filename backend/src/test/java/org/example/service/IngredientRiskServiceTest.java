package org.example.service;

import org.example.domain.Ingredient;
import org.example.domain.RiskLevel;
import org.example.dto.scan.RiskAssessmentResult;
import org.example.repository.IngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngredientRiskServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    private IngredientRiskService ingredientRiskService;

    @BeforeEach
    void setUp() {
        ingredientRiskService = new IngredientRiskService(ingredientRepository);

        when(ingredientRepository.findAll()).thenReturn(List.of(
                Ingredient.builder().name("아스파탐").riskLevel(RiskLevel.MEDIUM).description("인공감미료")
                        .aliases(java.util.Set.of("aspartame", "e951")).build(),
                Ingredient.builder().name("아질산나트륨").riskLevel(RiskLevel.HIGH).description("발색제")
                        .aliases(java.util.Set.of("sodium nitrite", "e250")).build()
        ));
    }

    @Test
    void 유해성분과_알레르기성분을_모두_찾아낸다() {
        List<String> ingredients = List.of("밀가루", "설탕", "아스파탐", "계란", "정제소금");

        RiskAssessmentResult result = ingredientRiskService.assess(ingredients);

        assertThat(result.getHarmfulIngredients()).hasSize(1);
        assertThat(result.getHarmfulIngredients().get(0).getIngredientName()).isEqualTo("아스파탐");
        assertThat(result.getAllergenMatches()).containsExactlyInAnyOrder("밀", "계란");
        assertThat(result.getOverallRisk()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void 고위험_성분이_있으면_전체위험도는_HIGH이다() {
        List<String> ingredients = List.of("아질산나트륨", "아스파탐");

        RiskAssessmentResult result = ingredientRiskService.assess(ingredients);

        assertThat(result.getOverallRisk()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void 영어_프랑스어_E번호_표기도_알아본다() {
        List<String> ingredients = List.of("Sucre", "lait écrémé en poudre", "NOISETTES 13%", "E951", "sodium nitrite");

        RiskAssessmentResult result = ingredientRiskService.assess(ingredients);

        assertThat(result.getHarmfulIngredients()).extracting(m -> m.getIngredientName())
                .containsExactly("아스파탐", "아질산나트륨");
        assertThat(result.getAllergenMatches()).containsExactly("우유", "헤이즐넛");
        assertThat(result.getOverallRisk()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void 같은_유해성분이_여러번_나와도_한번만_센다() {
        RiskAssessmentResult result = ingredientRiskService.assess(List.of("아스파탐", "감미료(아스파탐)"));

        assertThat(result.getHarmfulIngredients()).hasSize(1);
    }

    @Test
    void 매칭되는_성분이_없으면_위험도는_LOW이다() {
        List<String> ingredients = List.of("정제수", "정제소금");

        RiskAssessmentResult result = ingredientRiskService.assess(ingredients);

        assertThat(result.getHarmfulIngredients()).isEmpty();
        assertThat(result.getAllergenMatches()).isEmpty();
        assertThat(result.getOverallRisk()).isEqualTo(RiskLevel.LOW);
    }
}
