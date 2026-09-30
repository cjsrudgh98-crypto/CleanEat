package org.example.dto.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.example.service.CartService;

@Getter
@Setter
public class UpdateCartItemRequest {

    @NotNull(message = "수량은 필수입니다")
    @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
    @Max(value = CartService.MAX_QUANTITY, message = "한 상품은 " + CartService.MAX_QUANTITY + "개까지 담을 수 있습니다")
    private Integer quantity;
}
