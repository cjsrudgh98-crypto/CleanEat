package org.example.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 결제수단은 토스 결제창에서 고르므로 주문서에는 배송 정보만 받는다
@Getter
@Setter
public class CreateOrderRequest {

    @NotBlank(message = "수령인은 필수입니다")
    @Size(max = 50, message = "수령인은 50자 이하여야 합니다")
    private String recipientName;

    @NotBlank(message = "연락처는 필수입니다")
    @Size(max = 30, message = "연락처는 30자 이하여야 합니다")
    private String phone;

    @NotBlank(message = "배송지 주소는 필수입니다")
    @Size(max = 200, message = "배송지 주소는 200자 이하여야 합니다")
    private String address;

    @Size(max = 300, message = "요청사항은 300자 이하여야 합니다")
    private String requestNote;
}
