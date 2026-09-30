package org.example.dto.review;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 리뷰 목록 + 별점 요약.
 *
 * @param averageRating 소수점 한 자리 (리뷰가 없으면 null)
 * @param ratingCounts  별점별 리뷰 수 - [0]이 1점, [4]가 5점
 * @param myReview      로그인한 사용자가 쓴 리뷰 (없으면 null)
 * @param canWrite      지금 리뷰를 쓸 수 있는지
 * @param writeBlockedReason 쓸 수 없는 이유 - LOGIN_REQUIRED / NOT_PURCHASED / ALREADY_WRITTEN (쓸 수 있으면 null)
 */
public record ReviewListResponse(
        Long productId,
        Double averageRating,
        long reviewCount,
        long[] ratingCounts,
        List<ReviewItem> reviews,
        ReviewItem myReview,
        boolean canWrite,
        String writeBlockedReason) {

    /** @param mine 로그인한 사용자가 쓴 리뷰인지 (수정/삭제 버튼 노출용) */
    public record ReviewItem(Long id, String nickname, int rating, String content,
                             LocalDateTime createdAt, LocalDateTime updatedAt, boolean mine) {
    }
}
