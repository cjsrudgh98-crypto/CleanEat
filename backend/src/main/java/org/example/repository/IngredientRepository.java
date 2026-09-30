package org.example.repository;

import org.example.domain.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
    Optional<Ingredient> findByNameIgnoreCase(String name);

    // 예전 버전이 스캔 때마다 모르는 성분을 "안전" 등급으로 자동 등록하던 데이터 정리용 (DataSeeder에서 사용)
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM product_ingredients WHERE ingredient_id IN "
            + "(SELECT id FROM (SELECT id FROM ingredients WHERE description = :description) AS target)",
            nativeQuery = true)
    int unlinkFromProductsByDescription(@Param("description") String description);

    @Modifying
    @Transactional
    @Query("DELETE FROM Ingredient i WHERE i.description = :description")
    int deleteByDescription(@Param("description") String description);
}
