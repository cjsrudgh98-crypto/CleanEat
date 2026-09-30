package org.example.dto.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class OrderItemResponse {
    private Long id;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
    // 부분 취소된 상품인지 (주문 내역에서 줄을 긋고, 금액 합계에서 빠진다)
    private boolean cancelled;
}
