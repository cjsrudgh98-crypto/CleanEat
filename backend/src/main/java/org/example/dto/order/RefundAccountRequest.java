package org.example.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 가상계좌(무통장 입금)로 결제가 끝난 주문을 취소할 때 환불받을 계좌.
 * 카드와 달리 가상계좌는 토스가 원래 결제수단으로 돌려줄 수 없어서, 환불 계좌를 함께 보내야 한다.
 */
@Getter
@Setter
public class RefundAccountRequest {

    // 토스 은행 코드 두 자리 (예: "88" 신한은행) - BankCodes 참고
    @NotBlank(message = "환불 받을 은행을 선택해주세요")
    @Pattern(regexp = "^\\d{2}$", message = "은행 코드가 올바르지 않습니다")
    private String bankCode;

    @NotBlank(message = "환불 받을 계좌번호를 입력해주세요")
    @Pattern(regexp = "^[0-9-]{6,20}$", message = "계좌번호는 숫자와 -로 6~20자여야 합니다")
    private String accountNumber;

    @NotBlank(message = "예금주를 입력해주세요")
    @Size(max = 20, message = "예금주는 20자 이하여야 합니다")
    private String holderName;
}
