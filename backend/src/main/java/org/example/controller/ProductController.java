package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.product.ProductRecommendationResponse;
import org.example.dto.product.StoreListingResponse;
import org.example.service.ProductCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductCatalogService productCatalogService;

    // 비로그인도 조회 가능 - 로그인한 경우에만 내 알레르기 경고/식단 일치 여부가 채워진다
    @GetMapping
    public ResponseEntity<List<StoreListingResponse>> getAll(Authentication authentication) {
        return ResponseEntity.ok(productCatalogService.getAll(authentication));
    }

    // 마이페이지 식단/알레르기 설정 기반 맞춤 추천
    @GetMapping("/recommendations")
    public ResponseEntity<ProductRecommendationResponse> recommendations(Authentication authentication) {
        return ResponseEntity.ok(productCatalogService.recommend(authentication));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<StoreListingResponse> getOne(@PathVariable Long productId, Authentication authentication) {
        return ResponseEntity.ok(productCatalogService.getByProductId(productId, authentication));
    }
}
