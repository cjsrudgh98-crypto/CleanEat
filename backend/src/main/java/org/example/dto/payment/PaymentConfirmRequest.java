package org.example.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// 토스 결제창이 successUrl 쿼리스트링으로 돌려준 값 그대로
@Getter
@Setter
public class PaymentConfirmRequest {

    @NotBlank(message = "paymentKey가 없습니다")
    private String paymentKey;

    @NotBlank(message = "orderId가 없습니다")
    private String orderId;

    @NotNull(message = "amount가 없습니다")
    @Positive(message = "결제 금액이 올바르지 않습니다")
    private BigDecimal amount;
}
