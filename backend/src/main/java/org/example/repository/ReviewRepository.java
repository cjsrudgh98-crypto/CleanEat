package org.example.repository;

import org.example.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductIdOrderByCreatedAtDesc(Long productId);

    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);

    /** 상품별 [상품 id, 평균 별점, 리뷰 수] - 상품 목록에 별점을 한 번의 쿼리로 붙이기 위해 */
    @Query("select r.product.id, avg(r.rating), count(r) from Review r group by r.product.id")
    List<Object[]> summarizeAll();

    @Query("select avg(r.rating), count(r) from Review r where r.product.id = :productId")
    List<Object[]> summarize(@Param("productId") Long productId);
}
