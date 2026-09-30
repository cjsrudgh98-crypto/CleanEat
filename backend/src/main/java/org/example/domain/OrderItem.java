package org.example.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 시점의 상품명/단가를 스냅샷으로 저장한다 (이후 StoreListing 가격이 바뀌어도 과거 주문 내역은 그대로 유지됨).
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // 주문 취소 시 재고를 되돌리기 위한 참조. 상품이 나중에 판매중지돼도 주문 내역 표시는 스냅샷 필드로 계속 가능
    @ManyToOne
    @JoinColumn(name = "store_listing_id")
    private StoreListing storeListing;

    @Column(nullable = false, length = 200)
    private String productName;

    @Column(nullable = false, precision = 10, scale = 0)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    // 부분 취소된 상품 (이 줄만 환불/재고 복구됨). 주문 전체 취소는 주문 상태(CANCELLED)로 표시하고 이 값은 두지 않는다
    private LocalDateTime cancelledAt;

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    public BigDecimal lineAmount() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
