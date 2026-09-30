package org.example.repository;

import org.example.domain.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByBarcode(String barcode);

    /**
     * 대안 추천 후보 중 "판매하지 않는 상품"(바코드 스캔으로 쌓인 외부 DB 상품) - 전부 읽지 않고 DB에서 순위를 매겨 앞쪽만 가져온다.
     * 제외: 기준 상품 자신, 걸린 유해성분이 하나라도 든 상품. 순서: 가장 위험한 성분의 위험도 낮은 순 -> id 순.
     * harmfulNames가 비어 있으면 빈 IN ()이 되지 않도록 호출하는 쪽에서 아무것도 걸리지 않는 값을 넣는다.
     */
    @Query("""
            select p.id from Product p left join p.ingredients i
            where not exists (select l.id from StoreListing l where l.product = p)
              and p.id <> :excludeId
              and not exists (select h.id from Product hp join hp.ingredients h
                              where hp = p and h.name in :harmfulNames)
            group by p.id
            order by coalesce(max(case i.riskLevel
                                      when org.example.domain.RiskLevel.HIGH then 2
                                      when org.example.domain.RiskLevel.MEDIUM then 1
                                      else 0 end), 0),
                     p.id
            """)
    List<Long> findUnsoldRecommendationCandidateIds(@Param("excludeId") long excludeId,
                                                    @Param("harmfulNames") Collection<String> harmfulNames,
                                                    Pageable pageable);

    /** 영수증 상품명 비교용 - 성분 등 연관 데이터 없이 id/이름만 */
    @Query("select new org.example.repository.ProductRepository$ProductName(p.id, p.name) from Product p")
    List<ProductName> findAllNames();

    record ProductName(Long id, String name) {
    }
}
