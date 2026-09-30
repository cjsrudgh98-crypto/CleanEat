package org.example.dto.order;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

/** 고객 주문 취소. 본문은 없어도 되고, 입금이 끝난 가상계좌 주문만 환불 계좌가 필요하다 */
@Getter
@Setter
public class CancelOrderRequest {

    @Valid
    private RefundAccountRequest refundAccount;
}
