package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.OrderStatus;
import org.example.domain.Product;
import org.example.domain.Review;
import org.example.domain.User;
import org.example.dto.review.ReviewListResponse;
import org.example.dto.review.ReviewListResponse.ReviewItem;
import org.example.dto.review.ReviewRequest;
import org.example.exception.DuplicateResourceException;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.OrderRepository;
import org.example.repository.ReviewRepository;
import org.example.repository.StoreListingRepository;
import org.example.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 상품 리뷰·별점.
 *  - 판매 상품(StoreListing이 있는 상품)에만, 실제로 결제까지 마친 회원만, 상품당 하나 쓸 수 있다
 *  - 본인 리뷰만 수정/삭제 (관리자는 부적절한 리뷰를 삭제할 수 있음)
 *  - 탈퇴한 회원의 리뷰는 남고 작성자는 "탈퇴한 회원"으로 보인다 (회원 정보가 익명화되므로)
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    public static final String LOGIN_REQUIRED = "LOGIN_REQUIRED";
    public static final String NOT_PURCHASED = "NOT_PURCHASED";
    public static final String ALREADY_WRITTEN = "ALREADY_WRITTEN";

    // 결제가 끝나서 실제로 판매된 주문 (배송 준비~완료 포함)
    private static final Set<OrderStatus> PURCHASED = EnumSet.copyOf(
            Arrays.stream(OrderStatus.values()).filter(OrderStatus::isPurchased).toList());

    private final ReviewRepository reviewRepository;
    private final StoreListingRepository storeListingRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public ReviewListResponse list(Long productId, Authentication authentication) {
        Product product = findSoldProduct(productId);
        User user = optionalUser(authentication);
        List<Review> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(product.getId());

        long[] ratingCounts = new long[5];
        long sum = 0;
        for (Review r : reviews) {
            ratingCounts[r.getRating() - 1]++;
            sum += r.getRating();
        }
        Double average = reviews.isEmpty() ? null : Math.round(sum * 10.0 / reviews.size()) / 10.0;

        Long userId = user != null ? user.getId() : null;
        List<ReviewItem> items = reviews.stream().map(r -> toItem(r, userId)).toList();
        ReviewItem mine = items.stream().filter(ReviewItem::mine).findFirst().orElse(null);

        String blocked;
        if (user == null) blocked = LOGIN_REQUIRED;
        else if (mine != null) blocked = ALREADY_WRITTEN;
        else if (!orderRepository.hasPurchased(user.getId(), product.getId(), PURCHASED)) blocked = NOT_PURCHASED;
        else blocked = null;

        return new ReviewListResponse(product.getId(), average, reviews.size(), ratingCounts, items, mine,
                blocked == null, blocked);
    }

    @Transactional
    public ReviewItem create(ReviewRequest request, Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        if (request.getProductId() == null) {
            throw new IllegalArgumentException("리뷰를 쓸 상품이 없습니다");
        }
        Product product = findSoldProduct(request.getProductId());
        if (reviewRepository.findByUserIdAndProductId(user.getId(), product.getId()).isPresent()) {
            throw new DuplicateResourceException("이미 이 상품에 리뷰를 썼습니다. 기존 리뷰를 수정해주세요");
        }
        if (!orderRepository.hasPurchased(user.getId(), product.getId(), PURCHASED)) {
            throw new IllegalArgumentException("구매한 상품에만 리뷰를 쓸 수 있습니다");
        }
        Review review = reviewRepository.save(Review.builder()
                .user(user)
                .product(product)
                .rating(request.getRating())
                .content(normalize(request.getContent()))
                .build());
        return toItem(review, user.getId());
    }

    @Transactional
    public ReviewItem update(Long reviewId, ReviewRequest request, Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        Review review = findOwn(reviewId, user);
        review.setRating(request.getRating());
        review.setContent(normalize(request.getContent()));
        review.setUpdatedAt(LocalDateTime.now());
        return toItem(review, user.getId());
    }

    @Transactional
    public void delete(Long reviewId, Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        reviewRepository.delete(findOwn(reviewId, user));
    }

    /** 관리자: 부적절한 리뷰 삭제 */
    @Transactional
    public void adminDelete(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("리뷰를 찾을 수 없습니다: id=" + reviewId));
        reviewRepository.delete(review);
    }

    private Review findOwn(Long reviewId, User user) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("리뷰를 찾을 수 없습니다: id=" + reviewId));
        if (!review.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("본인이 쓴 리뷰만 수정/삭제할 수 있습니다");
        }
        return review;
    }

    private Product findSoldProduct(Long productId) {
        return storeListingRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("판매 중인 상품이 아닙니다: productId=" + productId))
                .getProduct();
    }

    // 리뷰 목록은 비로그인도 볼 수 있다 - 로그인했으면 내 리뷰 표시/작성 가능 여부에 쓴다
    private User optionalUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) return null;
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }

    private static String normalize(String content) {
        return content == null || content.isBlank() ? null : content.trim();
    }

    private static ReviewItem toItem(Review review, Long currentUserId) {
        return new ReviewItem(review.getId(), review.getUser().getNickname(), review.getRating(), review.getContent(),
                review.getCreatedAt(), review.getUpdatedAt(),
                currentUserId != null && currentUserId.equals(review.getUser().getId()));
    }
}
