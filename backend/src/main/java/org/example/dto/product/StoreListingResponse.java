package org.example.dto.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class StoreListingResponse {
    private Long productId;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private String imageUrl;
    private String description;
    private String category;
    private String categoryLabel;
    // 상품 알레르기 성분 / 맞는 식단(DietType 이름)
    private List<String> allergens;
    private List<String> diets;
    // 로그인 사용자의 알레르기 중 이 상품에 들어있는 것 (비로그인/해당 없음이면 빈 목록)
    private List<String> allergyWarnings;
    // 로그인 사용자가 식단을 설정했으면 그 식단에 맞는지, 설정 안 했으면 null
    private Boolean dietMatch;
    // 로그인한 사용자가 찜했는지 (비로그인이면 null)
    private Boolean favorite;
    // 로그인한 사용자가 재입고 알림을 신청했는지 (비로그인이면 null)
    private Boolean restockAlert;
    // 평균 별점 (소수점 한 자리, 리뷰가 없으면 null) / 리뷰 수
    private Double averageRating;
    private long reviewCount;
}
