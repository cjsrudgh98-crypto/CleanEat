package org.example.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.domain.Allergen;
import org.example.domain.DietType;
import org.example.domain.Ingredient;
import org.example.domain.Product;
import org.example.domain.ProductCategory;
import org.example.domain.RiskLevel;
import org.example.domain.StoreListing;
import org.example.repository.AllergenRepository;
import org.example.repository.IngredientRepository;
import org.example.repository.ProductRepository;
import org.example.repository.StoreListingRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final IngredientRepository ingredientRepository;
    private final AllergenRepository allergenRepository;
    private final ProductRepository productRepository;
    private final StoreListingRepository storeListingRepository;
    private final ObjectMapper objectMapper;

    private static final String AUTO_REGISTERED_DESCRIPTION = "자동 등록된 성분";
    private static final String STORE_LISTINGS_SEED = "seed/store-listings.json";
    private static final String HARMFUL_INGREDIENTS_SEED = "seed/harmful-ingredients.json";

    @Override
    public void run(String... args) {
        seedIngredients();
        seedAllergens();
        seedStoreListings();
        removeAutoRegisteredIngredients();
    }

    // 예전 버전은 바코드 스캔 때 모르는 성분을 "안전" 등급 유해성분으로 자동 등록했다.
    // 그 데이터가 남아 있으면 모든 성분이 유해성분 목록에 뜨므로 기동 시 정리한다 (없으면 아무 일도 안 함).
    private void removeAutoRegisteredIngredients() {
        ingredientRepository.unlinkFromProductsByDescription(AUTO_REGISTERED_DESCRIPTION);
        ingredientRepository.deleteByDescription(AUTO_REGISTERED_DESCRIPTION);
    }

    /**
     * 유해성분 사전 (resources/seed/harmful-ingredients.json). 운영 DB에도 새 성분이 들어가도록 이름 단위로 멱등하게 동작한다:
     *  - 없는 성분만 새로 넣는다 (관리자가 사용 중지한 성분도 "있는" 것이라 되살리지 않음)
     *  - 이미 있는 성분은 비어 있는 분류/별칭만 채운다 (관리자가 고친 위험도·설명·별칭은 건드리지 않음)
     */
    private void seedIngredients() {
        for (SeedIngredient seed : readSeed(HARMFUL_INGREDIENTS_SEED, new TypeReference<List<SeedIngredient>>() {})) {
            Ingredient existing = ingredientRepository.findByNameIgnoreCase(seed.name()).orElse(null);
            if (existing == null) {
                ingredientRepository.save(Ingredient.builder()
                        .name(seed.name())
                        .riskLevel(seed.riskLevel())
                        .category(seed.category())
                        .description(seed.description())
                        .aliases(new HashSet<>(seed.aliases()))
                        .enabled(true)
                        .build());
                continue;
            }
            boolean changed = false;
            if (existing.getCategory() == null) {
                existing.setCategory(seed.category());
                changed = true;
            }
            if (existing.getAliases().isEmpty()) {
                existing.getAliases().addAll(seed.aliases());
                changed = true;
            }
            if (changed) ingredientRepository.save(existing);
        }
    }

    private record SeedIngredient(String name, String category, RiskLevel riskLevel, String description,
                                  List<String> aliases) {}

    private void seedAllergens() {
        if (allergenRepository.count() > 0) {
            return;
        }
        List<Allergen> seed = List.of(
                Allergen.builder().name("우유").description("유제품 알레르기 유발 성분").build(),
                Allergen.builder().name("계란").description("난류 알레르기 유발 성분").build(),
                Allergen.builder().name("밀").description("글루텐 알레르기 유발 성분").build(),
                Allergen.builder().name("대두").description("콩류 알레르기 유발 성분").build(),
                Allergen.builder().name("땅콩").description("견과류 알레르기 유발 성분, 아나필락시스 위험").build(),
                Allergen.builder().name("호두").description("견과류 알레르기 유발 성분").build(),
                Allergen.builder().name("잣").description("견과류 알레르기 유발 성분").build(),
                Allergen.builder().name("견과류").description("견과류 전반 알레르기 유발 성분").build(),
                Allergen.builder().name("갑각류").description("새우, 게 등 갑각류 알레르기 유발 성분").build(),
                Allergen.builder().name("새우").description("갑각류 알레르기 유발 성분").build(),
                Allergen.builder().name("게").description("갑각류 알레르기 유발 성분").build(),
                Allergen.builder().name("오징어").description("연체동물 알레르기 유발 성분").build(),
                Allergen.builder().name("고등어").description("등푸른생선 알레르기 유발 성분").build(),
                Allergen.builder().name("메밀").description("메밀 알레르기 유발 성분").build(),
                Allergen.builder().name("돼지고기").description("돈육 알레르기 유발 성분").build(),
                Allergen.builder().name("복숭아").description("과일 알레르기 유발 성분").build(),
                Allergen.builder().name("토마토").description("과채류 알레르기 유발 성분").build(),
                Allergen.builder().name("아황산류").description("보존제, 천식 환자 주의 필요").build()
        );
        allergenRepository.saveAll(seed);
    }

    // 스캔 결과 추천 목록에서 "주문하기"가 뜨는 CleanEat 자체 판매 상품 데모 데이터 (resources/seed/store-listings.json).
    // 유해성분/알레르기 성분이 전혀 없는 제품으로 등록해서 어떤 위험 성분이 걸려도 대안으로 추천될 수 있게 한다.
    // 운영 DB처럼 데이터가 남아있는 경우를 위해 바코드 단위로 멱등하게 동작한다:
    // 없는 상품만 새로 등록하고, 이미 있는 상품은 비어 있는 카테고리/이미지/알레르기/식단만 채운다
    // (관리자 화면에서 바꾼 값과 가격/재고는 건드리지 않음).
    // 이미지는 frontend/public/images/products/{바코드}.jpg 로 프론트 빌드에 포함돼 같은 origin에서 서빙된다.
    private void seedStoreListings() {
        for (SeedListing seed : readSeedListings()) {
            Product product = productRepository.findByBarcode(seed.barcode())
                    .orElseGet(() -> productRepository.save(Product.builder()
                            .name(seed.name())
                            .barcode(seed.barcode())
                            .rawIngredientsText(seed.description())
                            .ingredients(Set.of())
                            .build()));

            Set<String> allergens = seed.allergens() != null ? new HashSet<>(seed.allergens()) : new HashSet<>();
            Set<DietType> diets = seed.diets() != null ? new HashSet<>(seed.diets()) : new HashSet<>();

            storeListingRepository.findByProductId(product.getId()).ifPresentOrElse(
                    listing -> {
                        // 관리자 화면에서 바꾼 값을 덮어쓰지 않도록, 비어 있는 값만 시드 파일로 채운다
                        boolean changed = false;
                        if (listing.getCategory() == null) {
                            listing.setCategory(seed.category());
                            changed = true;
                        }
                        if (listing.getImageUrl() == null && seed.imageUrl() != null) {
                            listing.setImageUrl(seed.imageUrl());
                            changed = true;
                        }
                        if (listing.getAllergens().isEmpty() && listing.getSuitableDiets().isEmpty()
                                && (!allergens.isEmpty() || !diets.isEmpty())) {
                            listing.getAllergens().addAll(allergens);
                            listing.getSuitableDiets().addAll(diets);
                            changed = true;
                        }
                        if (changed) {
                            storeListingRepository.save(listing);
                        }
                    },
                    () -> storeListingRepository.save(StoreListing.builder()
                            .product(product)
                            .category(seed.category())
                            .price(seed.price())
                            .stock(seed.stock())
                            .imageUrl(seed.imageUrl())
                            .description(seed.description())
                            .allergens(allergens)
                            .suitableDiets(diets)
                            .build()));
        }
    }

    private List<SeedListing> readSeedListings() {
        return readSeed(STORE_LISTINGS_SEED, new TypeReference<>() {});
    }

    private <T> T readSeed(String path, TypeReference<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("시드 파일을 읽지 못했습니다: " + path, e);
        }
    }

    private record SeedListing(String barcode, ProductCategory category, String name, BigDecimal price,
                               int stock, String imageUrl, String description,
                               List<String> allergens, List<DietType> diets) {}
}
