package org.example.repository;

import org.example.domain.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Favorite> findByUserIdAndStoreListingProductId(Long userId, Long productId);

    @Query("select f.storeListing.product.id from Favorite f where f.user.id = :userId")
    List<Long> findProductIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from Favorite f where f.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
