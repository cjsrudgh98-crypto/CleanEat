package org.example.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class CartItemResponse {
    private Long cartItemId;
    private Long productId;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private Integer availableStock;
    // 이 상품에 든 내 알레르기 성분 (없으면 빈 목록) - 장바구니/주문서에 경고 표시용
    private List<String> allergyWarnings;
}
