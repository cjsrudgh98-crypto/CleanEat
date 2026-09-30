package org.example.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// 재고 증감 (입고 +10, 파손 -2). 절대값으로 덮어쓰면 그 사이 결제로 줄어든 재고가 되살아나므로 증감으로만 받는다
@Getter
@Setter
public class StockAdjustRequest {

    @NotNull(message = "변경할 수량은 필수입니다")
    @Min(value = -100000, message = "한 번에 변경할 수 있는 수량을 넘었습니다")
    @Max(value = 100000, message = "한 번에 변경할 수 있는 수량을 넘었습니다")
    private Integer delta;
}
