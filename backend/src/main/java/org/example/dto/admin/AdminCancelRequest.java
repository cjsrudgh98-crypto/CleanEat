package org.example.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.example.dto.order.RefundAccountRequest;

@Getter
@Setter
public class AdminCancelRequest {

    // 비우면 "판매자 사정으로 주문 취소" - 고객 주문 내역과 토스 환불 사유에 표시된다
    @Size(max = 200, message = "취소 사유는 200자 이하여야 합니다")
    private String reason;

    // 입금이 끝난 가상계좌 주문을 취소할 때만 필요 (고객에게 받은 환불 계좌)
    @Valid
    private RefundAccountRequest refundAccount;
}
