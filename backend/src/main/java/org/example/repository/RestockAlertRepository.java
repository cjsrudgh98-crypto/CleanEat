package org.example.repository;

import org.example.domain.RestockAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RestockAlertRepository extends JpaRepository<RestockAlert, Long> {

    Optional<RestockAlert> findByUserIdAndStoreListingProductId(Long userId, Long productId);

    // 상품 목록에 "알림 신청됨"을 표시하기 위해 한 번에
    @Query("select a.storeListing.product.id from RestockAlert a where a.user.id = :userId")
    List<Long> findProductIdsByUserId(@Param("userId") Long userId);

    // 재고가 다시 생긴 상품의 신청 - 메일 보낼 회원 정보까지 한 번에
    @Query("select a from RestockAlert a join fetch a.user join fetch a.storeListing l join fetch l.product "
            + "where l.stock > 0")
    List<RestockAlert> findRestocked();

    @Query("select a from RestockAlert a join fetch a.user where a.storeListing.id = :listingId")
    List<RestockAlert> findByListingIdWithUser(@Param("listingId") Long listingId);

    @Modifying
    @Query("delete from RestockAlert a where a.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
