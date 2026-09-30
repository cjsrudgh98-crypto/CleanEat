package org.example.dto.scan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BarcodeScanRequest {

    @NotBlank(message = "바코드는 필수입니다")
    // 상품 바코드(EAN/UPC 숫자) 또는 관리자가 등록한 상품 코드 - AdminProductRequest와 같은 규칙, DB 컬럼 50자
    @Pattern(regexp = "^[0-9A-Za-z-]{4,50}$", message = "바코드는 영문/숫자 4~50자여야 합니다")
    private String barcode;
}
