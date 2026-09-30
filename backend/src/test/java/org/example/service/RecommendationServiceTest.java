package org.example.service;

import org.example.domain.DietType;
import org.example.domain.Ingredient;
import org.example.domain.Product;
import org.example.domain.RiskLevel;
import org.example.domain.StoreListing;
import org.example.dto.scan.ProductSummaryResponse;
import org.example.service.UserPreferenceService.Preferences;
import org.example.util.AllergenMatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판매하지 않는 상품의 순위는 DB 쿼리가 매기므로 목(mock) 대신 실제 JPA(H2)로 확인한다.
 */
@DataJpaTest
@Import(RecommendationService.class)
class RecommendationServiceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private RecommendationService recommendationService;

    private Ingredient ingredient(String name, RiskLevel riskLevel) {
        return em.persist(Ingredient.builder().name(name).riskLevel(riskLevel).build());
    }

    private Product product(String name, Ingredient... ingredients) {
        return em.persist(Product.builder().name(name).barcode("B-" + name)
                .ingredients(new HashSet<>(Set.of(ingredients))).build());
    }

    private StoreListing listing(Product product, Set<String> allergens, Set<DietType> diets) {
        return em.persist(StoreListing.builder().product(product).price(BigDecimal.TEN).stock(10)
                .allergens(new HashSet<>(allergens)).suitableDiets(new HashSet<>(diets)).build());
    }

    private List<String> names(List<ProductSummaryResponse> result) {
        return result.stream().map(ProductSummaryResponse::getName).toList();
    }

    private Preferences prefs(Set<DietType> diets, String... allergies) {
        return new Preferences(true, diets, List.of(allergies), AllergenMatcher.expand(List.of(allergies)));
    }

    @Test
    void 유해성분이_포함된_제품은_추천에서_제외한다() {
        product("위험 과자", ingredient("아질산나트륨", RiskLevel.HIGH));
        product("안전 과자", ingredient("정제수", RiskLevel.LOW));
        em.flush();

        List<ProductSummaryResponse> result = recommendationService.recommend(null, Set.of("아질산나트륨"));

        assertThat(names(result)).containsExactly("안전 과자");
    }

    @Test
    void 조회한_제품_자신은_추천에서_제외한다() {
        Product self = product("본인 제품", ingredient("정제수", RiskLevel.LOW));
        Product sold = product("판매 본인 제품");
        listing(sold, Set.of(), Set.of());
        em.flush();

        assertThat(recommendationService.recommend(self.getId(), Set.of())).extracting(ProductSummaryResponse::getName)
                .containsExactly("판매 본인 제품");
        assertThat(recommendationService.recommend(sold.getId(), Set.of())).extracting(ProductSummaryResponse::getName)
                .containsExactly("본인 제품");
    }

    @Test
    void 결과는_최대5개까지만_반환한다() {
        IntStream.range(0, 6).forEach(i -> product("상품" + i));
        em.flush();

        assertThat(recommendationService.recommend(null, Set.of())).hasSize(5);
    }

    @Test
    void 위험도가_낮은_제품부터_정렬해서_반환한다() {
        product("고위험", ingredient("성분H", RiskLevel.HIGH));
        product("저위험", ingredient("성분L", RiskLevel.LOW));
        product("중위험", ingredient("성분M", RiskLevel.MEDIUM));
        product("성분없음");
        em.flush();

        List<ProductSummaryResponse> result = recommendationService.recommend(null, Set.of());

        // 성분 정보가 없는 상품은 "안전"과 같은 순위, 같으면 먼저 등록된 순
        assertThat(names(result)).containsExactly("저위험", "성분없음", "중위험", "고위험");
    }

    @Test
    void 판매하지_않는_상품이_많아도_위험도가_낮은_것만_골라_판매상품과_함께_정렬한다() {
        Ingredient high = ingredient("성분H", RiskLevel.HIGH);
        Ingredient medium = ingredient("성분M", RiskLevel.MEDIUM);
        // 위험 상품이 먼저 잔뜩 쌓여 있어도 DB가 위험도 순으로 앞쪽만 골라 줘야 한다
        IntStream.range(0, 20).forEach(i -> product("위험" + i, high));
        product("보통 외부");
        product("주의 외부", medium);
        listing(product("판매 주의", medium), Set.of(), Set.of());
        listing(product("판매 안전"), Set.of(), Set.of());
        em.flush();

        List<ProductSummaryResponse> result = recommendationService.recommend(null, Set.of());

        assertThat(names(result)).containsExactly("보통 외부", "판매 안전", "주의 외부", "판매 주의", "위험0");
        assertThat(result.get(1).getPrice()).isEqualByComparingTo("10");
    }

    @Test
    void 사용자_알레르기_성분이_든_판매상품은_추천에서_제외한다() {
        listing(product("구운 아몬드"), Set.of("아몬드"), Set.of());
        listing(product("현미 과자"), Set.of(), Set.of());
        em.flush();

        // "견과류"로 등록해도 아몬드가 걸러져야 한다
        List<ProductSummaryResponse> result = recommendationService.recommend(null, Set.of(), prefs(Set.of(), "견과류"));

        assertThat(names(result)).containsExactly("현미 과자");
    }

    @Test
    void 같은_위험도면_사용자_식단에_맞는_상품을_먼저_추천한다() {
        listing(product("치즈"), Set.of("우유"), Set.of(DietType.VEGETARIAN));
        listing(product("두부"), Set.of("대두"), Set.of(DietType.VEGAN, DietType.VEGETARIAN));
        product("외부 과자");
        em.flush();

        List<ProductSummaryResponse> result = recommendationService.recommend(null, Set.of(), prefs(Set.of(DietType.VEGAN)));

        // 식단에 맞는 판매 상품 -> 나머지는 id 순
        assertThat(names(result)).containsExactly("두부", "치즈", "외부 과자");
    }
}
