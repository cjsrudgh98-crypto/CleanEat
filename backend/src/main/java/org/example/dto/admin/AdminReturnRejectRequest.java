package org.example.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 관리자 반품 거절 - 사유는 고객 주문 내역에 그대로 보인다 */
@Getter
@Setter
public class AdminReturnRejectRequest {

    @NotBlank(message = "거절 사유를 입력해주세요")
    @Size(max = 300, message = "거절 사유는 300자 이하여야 합니다")
    private String reason;
}
