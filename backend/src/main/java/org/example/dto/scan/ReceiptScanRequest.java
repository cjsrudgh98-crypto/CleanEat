package org.example.dto.scan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 영수증의 QR코드/바코드를 읽은 원문 그대로 (주문번호, 상품 바코드 목록, URL 등)
@Getter
@Setter
public class ReceiptScanRequest {

    @NotBlank(message = "영수증 코드가 비어 있습니다")
    @Size(max = 4000, message = "영수증 코드가 너무 깁니다")
    private String code;
}
