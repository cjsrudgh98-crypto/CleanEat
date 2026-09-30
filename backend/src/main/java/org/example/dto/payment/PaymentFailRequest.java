package org.example.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// 토스 결제창이 failUrl 쿼리스트링으로 돌려준 값 (사용자가 결제창을 닫은 경우 포함)
@Getter
@Setter
public class PaymentFailRequest {

    @NotBlank(message = "orderId가 없습니다")
    private String orderId;

    private String code;

    private String message;
}
