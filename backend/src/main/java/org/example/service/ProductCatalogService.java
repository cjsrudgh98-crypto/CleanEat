package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.Favorite;
import org.example.domain.ProductCategory;
import org.example.domain.StoreListing;
import org.example.dto.product.ProductRecommendationResponse;
import org.example.dto.product.StoreListingResponse;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.FavoriteRepository;
import org.example.repository.RestockAlertRepository;
import org.example.repository.ReviewRepository;
import org.example.repository.StoreListingRepository;
import org.example.repository.UserRepository;
import org.example.security.AppUserDetails;
import org.example.service.UserPreferenceService.Preferences;
import org.example.util.AllergenMatcher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    private static final int RECOMMENDATION_LIMIT = 8;

    private final StoreListingRepository storeListingRepository;
    private final UserPreferenceService userPreferenceService;
    private final ReviewRepository reviewRepository;
    private final FavoriteRepository favoriteRepository;
    private final RestockAlertRepository restockAlertRepository;
    private final UserRepository userRepository;

    /** 상품 응답에 붙이는 사용자별/상품별 부가 정보 - 목록 전체에 대해 한 번씩만 조회한다 */
    private record Context(Preferences prefs, Set<Long> favoriteProductIds, Set<Long> restockAlertProductIds,
                           Map<Long, Rating> ratings) {
    }

    private record Rating(double average, long count) {
    }

    // 카테고리 순서(enum 선언 순) -> 이름 순으로 정렬해서 내려준다
    @Transactional(readOnly = true)
    public List<StoreListingResponse> getAll(Authentication authentication) {
        Context ctx = context(authentication, allRatings());
        return sortedListings().stream().map(listing -> toResponse(listing, ctx)).toList();
    }

    @Transactional(readOnly = true)
    public StoreListingResponse getByProductId(Long productId, Authentication authentication) {
        StoreListing listing = storeListingRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("판매 중인 상품이 아닙니다: productId=" + productId));
        return toResponse(listing, context(authentication, ratingOf(productId)));
    }

    /** 내가 찜한 상품 (최근에 찜한 순) */
    @Transactional(readOnly = true)
    public List<StoreListingResponse> favorites(Authentication authentication) {
        Long userId = currentUserId(authentication);
        if (userId == null) return List.of();
        Context ctx = context(authentication, allRatings());
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(Favorite::getStoreListing)
                .map(listing -> toResponse(listing, ctx))
                .toList();
    }

    /**
     * 맞춤 추천: 품절 상품과 알레르기 성분이 든 상품은 빼고, 식단을 설정했으면 고른 식단을 모두 만족하는 상품만 고른다.
     * 한 카테고리에 몰리지 않게 카테고리를 돌아가며 하나씩 뽑는다.
     */
    @Transactional(readOnly = true)
    public ProductRecommendationResponse recommend(Authentication authentication) {
        Context ctx = context(authentication, allRatings());
        Preferences prefs = ctx.prefs();

        int excludedByAllergy = 0;
        Map<ProductCategory, Deque<StoreListing>> byCategory = new EnumMap<>(ProductCategory.class);
        for (StoreListing listing : sortedListings()) {
            if (listing.getStock() <= 0) continue;
            if (!AllergenMatcher.matches(listing.getAllergens(), prefs.expandedAllergens()).isEmpty()) {
                excludedByAllergy++;
                continue;
            }
            if (!prefs.fitsDiet(listing.getSuitableDiets())) continue;
            byCategory.computeIfAbsent(categoryOf(listing), c -> new ArrayDeque<>()).add(listing);
        }

        List<StoreListingResponse> picks = new ArrayList<>();
        while (picks.size() < RECOMMENDATION_LIMIT && byCategory.values().stream().anyMatch(q -> !q.isEmpty())) {
            for (Deque<StoreListing> queue : byCategory.values()) {
                if (picks.size() >= RECOMMENDATION_LIMIT) break;
                StoreListing next = queue.poll();
                if (next != null) picks.add(toResponse(next, ctx));
            }
        }

        return new ProductRecommendationResponse(
                prefs.loggedIn(),
                prefs.diets().stream().sorted().map(Enum::name).toList(),
                prefs.dietLabel(),
                prefs.allergies(),
                excludedByAllergy,
                picks);
    }

    private Context context(Authentication authentication, Map<Long, Rating> ratings) {
        Preferences prefs = userPreferenceService.of(authentication);
        Long userId = prefs.loggedIn() ? currentUserId(authentication) : null;
        Set<Long> favorites = userId != null ? new HashSet<>(favoriteRepository.findProductIdsByUserId(userId)) : null;
        Set<Long> restockAlerts = userId != null ? new HashSet<>(restockAlertRepository.findProductIdsByUserId(userId)) : null;
        return new Context(prefs, favorites, restockAlerts, ratings);
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) return null;
        Long userId = AppUserDetails.userIdOf(authentication);
        if (userId != null) return userId;
        return userRepository.findByUsername(authentication.getName()).map(u -> u.getId()).orElse(null);
    }

    private Map<Long, Rating> allRatings() {
        Map<Long, Rating> ratings = new HashMap<>();
        for (Object[] row : reviewRepository.summarizeAll()) {
            ratings.put((Long) row[0], new Rating(((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()));
        }
        return ratings;
    }

    private Map<Long, Rating> ratingOf(Long productId) {
        List<Object[]> rows = reviewRepository.summarize(productId);
        if (rows.isEmpty() || rows.get(0)[0] == null) return Map.of();
        Object[] row = rows.get(0);
        return Map.of(productId, new Rating(((Number) row[0]).doubleValue(), ((Number) row[1]).longValue()));
    }

    private List<StoreListing> sortedListings() {
        return storeListingRepository.findAll().stream()
                .sorted(Comparator.comparing(ProductCatalogService::categoryOf)
                        .thenComparing(listing -> listing.getProduct().getName()))
                .toList();
    }

    // 카테고리 컬럼 추가 전에 등록된 상품은 null일 수 있어서 "기타"로 취급한다
    private static ProductCategory categoryOf(StoreListing listing) {
        return listing.getCategory() != null ? listing.getCategory() : ProductCategory.ETC;
    }

    /** 평균 별점은 소수점 한 자리로 (4.25 -> 4.3) */
    static Double roundRating(double average) {
        return Math.round(average * 10) / 10.0;
    }

    private StoreListingResponse toResponse(StoreListing listing, Context ctx) {
        ProductCategory category = categoryOf(listing);
        Preferences prefs = ctx.prefs();
        Long productId = listing.getProduct().getId();
        Rating rating = ctx.ratings().get(productId);
        return new StoreListingResponse(
                productId,
                listing.getProduct().getName(),
                listing.getPrice(),
                listing.getStock(),
                listing.getImageUrl(),
                listing.getDescription(),
                category.name(),
                category.getLabel(),
                listing.getAllergens().stream().sorted().toList(),
                listing.getSuitableDiets().stream().sorted().map(Enum::name).toList(),
                AllergenMatcher.matches(listing.getAllergens(), prefs.expandedAllergens()),
                prefs.hasDiet() ? prefs.fitsDiet(listing.getSuitableDiets()) : null,
                ctx.favoriteProductIds() != null ? ctx.favoriteProductIds().contains(productId) : null,
                ctx.restockAlertProductIds() != null ? ctx.restockAlertProductIds().contains(productId) : null,
                rating != null ? roundRating(rating.average()) : null,
                rating != null ? rating.count() : 0);
    }
}
