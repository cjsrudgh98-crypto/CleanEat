package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.review.ReviewListResponse;
import org.example.dto.review.ReviewListResponse.ReviewItem;
import org.example.dto.review.ReviewRequest;
import org.example.service.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 상품 리뷰. 목록은 /api/products/** 아래라 비로그인도 볼 수 있고,
 * 작성/수정/삭제는 /api/reviews 아래라 로그인이 필요하다 (SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/api/products/{productId}/reviews")
    public ResponseEntity<ReviewListResponse> list(@PathVariable Long productId, Authentication authentication) {
        return ResponseEntity.ok(reviewService.list(productId, authentication));
    }

    @PostMapping("/api/reviews")
    public ResponseEntity<ReviewItem> create(@Valid @RequestBody ReviewRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(request, authentication));
    }

    @PutMapping("/api/reviews/{reviewId}")
    public ResponseEntity<ReviewItem> update(@PathVariable Long reviewId, @Valid @RequestBody ReviewRequest request,
                                             Authentication authentication) {
        return ResponseEntity.ok(reviewService.update(reviewId, request, authentication));
    }

    @DeleteMapping("/api/reviews/{reviewId}")
    public ResponseEntity<Void> delete(@PathVariable Long reviewId, Authentication authentication) {
        reviewService.delete(reviewId, authentication);
        return ResponseEntity.noContent().build();
    }

    // 관리자: 부적절한 리뷰 삭제 (/api/admin/** 은 ADMIN 권한 필요)
    @DeleteMapping("/api/admin/reviews/{reviewId}")
    public ResponseEntity<Void> adminDelete(@PathVariable Long reviewId) {
        reviewService.adminDelete(reviewId);
        return ResponseEntity.noContent().build();
    }
}
