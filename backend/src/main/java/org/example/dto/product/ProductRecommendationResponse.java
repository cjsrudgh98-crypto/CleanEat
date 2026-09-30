package org.example.dto.product;

import java.util.List;

/**
 * 마이페이지 식단/알레르기 설정 기반 맞춤 추천.
 * excludedByAllergy: 알레르기 때문에 추천 후보에서 빠진 상품 수 (화면에 "N개 제외" 안내용)
 */
public record ProductRecommendationResponse(
        boolean loggedIn,
        List<String> dietTypes,
        String dietLabel,
        List<String> allergies,
        int excludedByAllergy,
        List<StoreListingResponse> products) {
}
