package org.example.service;

import org.example.domain.DietType;
import org.example.domain.Product;
import org.example.domain.ProductCategory;
import org.example.domain.StoreListing;
import org.example.dto.product.ProductRecommendationResponse;
import org.example.dto.product.StoreListingResponse;
import org.example.repository.FavoriteRepository;
import org.example.repository.ReviewRepository;
import org.example.repository.StoreListingRepository;
import org.example.repository.UserRepository;
import org.example.security.AppUserDetails;
import org.example.service.UserPreferenceService.Preferences;
import org.example.util.AllergenMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTest {

    @Mock
    private StoreListingRepository storeListingRepository;

    @Mock
    private UserPreferenceService userPreferenceService;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @org.mockito.Mock
    private org.example.repository.RestockAlertRepository restockAlertRepository;

    private ProductCatalogService productCatalogService;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        productCatalogService = new ProductCatalogService(storeListingRepository, userPreferenceService, reviewRepository,
                favoriteRepository, restockAlertRepository, userRepository);
    }

    private StoreListing listing(String name, ProductCategory category, int stock, Set<String> allergens, Set<DietType> diets) {
        long id = nextId++;
        Product product = Product.builder().id(id).name(name).barcode("88" + id).build();
        return StoreListing.builder().id(id).product(product).category(category).price(BigDecimal.TEN).stock(stock)
                .allergens(new HashSet<>(allergens)).suitableDiets(new HashSet<>(diets)).build();
    }

    private void givenUser(Set<DietType> diets, String... allergies) {
        when(userPreferenceService.of(authentication)).thenReturn(
                new Preferences(true, diets, List.of(allergies), AllergenMatcher.expand(List.of(allergies))));
    }

    @Test
    void 맞춤추천은_알레르기_상품과_식단에_안맞는_상품과_품절상품을_뺀다() {
        givenUser(Set.of(DietType.VEGAN), "견과류");
        when(storeListingRepository.findAll()).thenReturn(List.of(
                listing("구운 아몬드", ProductCategory.NUTS, 10, Set.of("아몬드"), Set.of(DietType.VEGAN)),
                listing("그릭요거트", ProductCategory.DAIRY, 10, Set.of("우유"), Set.of(DietType.VEGETARIAN)),
                listing("두부", ProductCategory.PROTEIN, 10, Set.of("대두"), Set.of(DietType.VEGAN)),
                listing("건대추", ProductCategory.NUTS, 0, Set.of(), Set.of(DietType.VEGAN)),
                listing("현미 과자", ProductCategory.SNACK, 10, Set.of(), Set.of(DietType.VEGAN))));

        ProductRecommendationResponse result = productCatalogService.recommend(authentication);

        assertThat(result.dietLabel()).isEqualTo("비건");
        assertThat(result.excludedByAllergy()).isEqualTo(1);
        assertThat(result.products()).extracting(StoreListingResponse::getName)
                .containsExactlyInAnyOrder("두부", "현미 과자");
    }

    @Test
    void 맞춤추천은_카테고리를_돌아가며_최대_8개를_고른다() {
        givenUser(Set.of());
        List<StoreListing> listings = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) listings.add(listing("과자" + i, ProductCategory.SNACK, 10, Set.of(), Set.of()));
        for (int i = 0; i < 6; i++) listings.add(listing("음료" + i, ProductCategory.BEVERAGE, 10, Set.of(), Set.of()));
        when(storeListingRepository.findAll()).thenReturn(listings);

        ProductRecommendationResponse result = productCatalogService.recommend(authentication);

        assertThat(result.products()).hasSize(8);
        assertThat(result.products()).extracting(StoreListingResponse::getCategory)
                .containsExactly("SNACK", "BEVERAGE", "SNACK", "BEVERAGE", "SNACK", "BEVERAGE", "SNACK", "BEVERAGE");
    }

    @Test
    void 상품목록에는_내_알레르기_경고와_식단_일치여부가_담긴다() {
        givenUser(Set.of(DietType.VEGAN), "유제품");
        when(storeListingRepository.findAll()).thenReturn(List.of(
                listing("그릭요거트", ProductCategory.DAIRY, 10, Set.of("우유"), Set.of(DietType.VEGETARIAN)),
                listing("두유", ProductCategory.DAIRY, 10, Set.of("대두"), Set.of(DietType.VEGAN))));

        List<StoreListingResponse> result = productCatalogService.getAll(authentication);

        assertThat(result.get(0).getName()).isEqualTo("그릭요거트");
        assertThat(result.get(0).getAllergyWarnings()).containsExactly("우유");
        assertThat(result.get(0).getDietMatch()).isFalse();
        assertThat(result.get(1).getAllergyWarnings()).isEmpty();
        assertThat(result.get(1).getDietMatch()).isTrue();
    }

    @Test
    void 식단을_여러개_고르면_모두_만족하는_상품만_추천한다() {
        givenUser(Set.of(DietType.VEGAN, DietType.GLUTEN_FREE));
        when(storeListingRepository.findAll()).thenReturn(List.of(
                listing("통밀 크래커", ProductCategory.SNACK, 10, Set.of("밀"), Set.of(DietType.VEGAN)),
                listing("현미 과자", ProductCategory.SNACK, 10, Set.of(), Set.of(DietType.VEGAN, DietType.GLUTEN_FREE)),
                listing("구운 계란", ProductCategory.PROTEIN, 10, Set.of("계란"), Set.of(DietType.GLUTEN_FREE))));

        ProductRecommendationResponse result = productCatalogService.recommend(authentication);

        assertThat(result.dietLabel()).isEqualTo("비건·글루텐프리");
        assertThat(result.products()).extracting(StoreListingResponse::getName).containsExactly("현미 과자");
    }

    @Test
    void 비로그인이면_경고와_식단정보가_비어있다() {
        when(userPreferenceService.of(null)).thenReturn(Preferences.NONE);
        when(storeListingRepository.findAll()).thenReturn(List.of(
                listing("그릭요거트", ProductCategory.DAIRY, 10, Set.of("우유"), Set.of(DietType.VEGETARIAN))));

        List<StoreListingResponse> result = productCatalogService.getAll(null);

        assertThat(result.get(0).getAllergyWarnings()).isEmpty();
        assertThat(result.get(0).getDietMatch()).isNull();
        assertThat(result.get(0).getAllergens()).containsExactly("우유");
    }

    @Test
    void 로그인_정보에_회원id가_있으면_회원을_다시_조회하지_않는다() {
        AppUserDetails principal = new AppUserDetails(7L, "cleaneat_user", "x",
                List.of(new SimpleGrantedAuthority("ROLE_USER")), 0);
        Authentication login = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(userPreferenceService.of(login)).thenReturn(new Preferences(true, Set.of(), List.of(), Set.of()));
        when(favoriteRepository.findProductIdsByUserId(7L)).thenReturn(List.of(1L));
        when(storeListingRepository.findAll()).thenReturn(List.of(
                listing("그릭요거트", ProductCategory.DAIRY, 10, Set.of("우유"), Set.of(DietType.VEGETARIAN))));

        List<StoreListingResponse> result = productCatalogService.getAll(login);

        assertThat(result.get(0).getFavorite()).isTrue();
        verifyNoInteractions(userRepository);
    }
}
