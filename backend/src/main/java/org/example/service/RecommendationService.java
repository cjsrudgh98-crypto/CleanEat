package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.Ingredient;
import org.example.domain.Product;
import org.example.domain.RiskLevel;
import org.example.domain.StoreListing;
import org.example.dto.scan.ProductSummaryResponse;
import org.example.repository.ProductRepository;
import org.example.repository.StoreListingRepository;
import org.example.service.UserPreferenceService.Preferences;
import org.example.util.AllergenMatcher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 5;
    // 유해성분이 없을 때 빈 IN ()을 피하려고 넣는 값 - 이런 이름의 성분은 없다
    private static final Set<String> NO_HARMFUL_NAMES = Set.of("");

    private final ProductRepository productRepository;
    private final StoreListingRepository storeListingRepository;

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> recommend(Long excludeProductId, Set<String> harmfulIngredientNames) {
        return recommend(excludeProductId, harmfulIngredientNames, Preferences.NONE);
    }

    /**
     * 스캔한 제품의 대안 추천.
     * - 스캔 결과에서 걸린 유해성분이 든 제품은 제외
     * - 로그인 사용자의 알레르기 성분이 든 판매 상품은 제외
     * - 위험도가 낮은 순, 같으면 사용자 식단에 맞는 판매 상품을 먼저, 그다음 id 순
     *
     * 후보는 두 갈래로 모은다 (스캔할 때마다 상품 전체를 읽지 않도록):
     * - 판매 상품: 관리자가 등록한 만큼이라 수가 적어서 전부 읽어 알레르기/식단까지 따진다
     * - 판매하지 않는 상품: 바코드 스캔으로 계속 쌓이므로 DB가 위험도 순으로 앞쪽 몇 개만 준다
     *   (알레르기 정보·식단이 없어서 순서가 위험도와 id로만 정해지므로, 앞쪽 DEFAULT_LIMIT개면 충분하다)
     */
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> recommend(Long excludeProductId, Set<String> harmfulIngredientNames,
                                                  Preferences prefs) {
        Map<Long, StoreListing> listingsByProductId = storeListingRepository.findAll().stream()
                .collect(Collectors.toMap(listing -> listing.getProduct().getId(), Function.identity()));

        List<Product> candidates = new ArrayList<>();
        listingsByProductId.values().stream()
                .map(StoreListing::getProduct)
                .filter(product -> excludeProductId == null || !product.getId().equals(excludeProductId))
                .filter(product -> product.getIngredients().stream()
                        .noneMatch(ingredient -> harmfulIngredientNames.contains(ingredient.getName())))
                .filter(product -> !containsUserAllergen(listingsByProductId.get(product.getId()), prefs))
                .forEach(candidates::add);
        candidates.addAll(unsoldCandidates(excludeProductId, harmfulIngredientNames));

        return candidates.stream()
                .sorted(Comparator.comparing(this::worstRiskLevel, Comparator.comparingInt(this::severity))
                        .thenComparing(product -> !fitsDiet(listingsByProductId.get(product.getId()), prefs))
                        .thenComparing(Product::getId))
                .limit(DEFAULT_LIMIT)
                .map(product -> {
                    Optional<StoreListing> listing = Optional.ofNullable(listingsByProductId.get(product.getId()));
                    return new ProductSummaryResponse(
                            product.getId(), product.getName(), product.getBarcode(), worstRiskLevel(product),
                            listing.map(StoreListing::getPrice).orElse(null),
                            listing.map(StoreListing::getImageUrl).orElse(null));
                })
                .collect(Collectors.toList());
    }

    private List<Product> unsoldCandidates(Long excludeProductId, Set<String> harmfulIngredientNames) {
        List<Long> ids = productRepository.findUnsoldRecommendationCandidateIds(
                excludeProductId != null ? excludeProductId : -1L,
                harmfulIngredientNames.isEmpty() ? NO_HARMFUL_NAMES : harmfulIngredientNames,
                PageRequest.of(0, DEFAULT_LIMIT));
        return ids.isEmpty() ? List.of() : productRepository.findAllById(ids);
    }

    private static boolean containsUserAllergen(StoreListing listing, Preferences prefs) {
        return listing != null && !AllergenMatcher.matches(listing.getAllergens(), prefs.expandedAllergens()).isEmpty();
    }

    private static boolean fitsDiet(StoreListing listing, Preferences prefs) {
        if (!prefs.hasDiet()) return true;
        return listing != null && prefs.fitsDiet(listing.getSuitableDiets());
    }

    private RiskLevel worstRiskLevel(Product product) {
        return product.getIngredients().stream()
                .map(Ingredient::getRiskLevel)
                .max(Comparator.comparingInt(this::severity))
                .orElse(RiskLevel.LOW);
    }

    private int severity(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
        };
    }
}
