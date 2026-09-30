package org.example.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Product(OpenFoodFacts 원본 데이터)와 별개로, CleanEat이 실제로 판매하는 상품의 커머스 정보(가격/재고)만 담는다.
 * Product를 직접 오염시키지 않기 위해 분리했다 - 모든 Product가 판매 대상은 아니고,
 * StoreListing이 있는 Product만 "주문 가능"으로 취급한다.
 */
@Entity
@Table(name = "store_listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false, precision = 10, scale = 0)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private ProductCategory category;

    // 식약처 알레르기 유발물질 표시 대상 기준 이름 (예: "우유", "대두", "밀", "아몬드")
    // 사용자 알레르기와의 비교는 AllergenMatcher가 묶음("견과류" -> 아몬드/호두/잣...)까지 고려해서 한다
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "store_listing_allergens", joinColumns = @JoinColumn(name = "store_listing_id"))
    @Column(name = "allergen", length = 30)
    @Builder.Default
    private Set<String> allergens = new HashSet<>();

    // 이 상품이 맞는 식단 (예: 두부 -> 비건/베지테리언/케토/글루텐프리/저나트륨)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "store_listing_diets", joinColumns = @JoinColumn(name = "store_listing_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "diet_type", length = 20)
    @Builder.Default
    private Set<DietType> suitableDiets = new HashSet<>();
}
