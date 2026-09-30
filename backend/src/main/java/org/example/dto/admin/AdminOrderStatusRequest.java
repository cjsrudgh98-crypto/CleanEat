package org.example.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.example.domain.OrderStatus;

@Getter
@Setter
public class AdminOrderStatusRequest {

    @NotNull(message = "변경할 상태는 필수입니다")
    private OrderStatus status;

    // 배송중(SHIPPING)으로 바꿀 때만 필요
    @Size(max = 30, message = "택배사는 30자 이하여야 합니다")
    private String courier;

    @Size(max = 50, message = "운송장 번호는 50자 이하여야 합니다")
    private String trackingNumber;
}
