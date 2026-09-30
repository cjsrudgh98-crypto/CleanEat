package org.example.dto.scan;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.domain.RiskLevel;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ProductSummaryResponse {
    private Long id;
    private String name;
    private String barcode;
    private RiskLevel riskLevel;
    // CleanEat이 실제로 판매하는 상품이면 값이 있고, 아니면 null (프론트에서 "담기" 버튼 노출 여부 판단용)
    private BigDecimal price;
    private String imageUrl;
}
