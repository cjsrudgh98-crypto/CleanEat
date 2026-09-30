package org.example.repository;

import jakarta.persistence.LockModeType;
import org.example.domain.StoreListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StoreListingRepository extends JpaRepository<StoreListing, Long> {
    Optional<StoreListing> findByProductId(Long productId);

    // 관리자 재고 조정 - 결제(재고 차감)와 동시에 일어나도 한쪽 변경이 사라지지 않게 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from StoreListing l where l.product.id = :productId")
    Optional<StoreListing> findByProductIdForUpdate(@Param("productId") Long productId);

    long countByStockLessThanEqual(int stock);

    long countByStockBetween(int min, int max);
}
