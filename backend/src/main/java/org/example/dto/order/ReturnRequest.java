package org.example.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 고객 반품 신청. 가상계좌로 결제한 주문만 환불 계좌가 필요하다 */
@Getter
@Setter
public class ReturnRequest {

    @NotBlank(message = "반품 사유를 입력해주세요")
    @Size(max = 300, message = "반품 사유는 300자 이하여야 합니다")
    private String reason;

    @Valid
    private RefundAccountRequest refundAccount;
}
