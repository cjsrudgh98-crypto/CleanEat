package org.example.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.example.domain.DietType;
import org.example.domain.ProductCategory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 상품 등록/수정. 재고(stock)는 등록할 때만 쓰고, 수정 시에는 무시한다
 * (판매 중 재고는 동시 결제와 충돌하지 않도록 증감 API로만 바꾼다).
 */
@Getter
@Setter
public class AdminProductRequest {

    @NotBlank(message = "상품명은 필수입니다")
    @Size(max = 200, message = "상품명은 200자 이하여야 합니다")
    private String name;

    @NotBlank(message = "바코드는 필수입니다")
    @Pattern(regexp = "^[0-9A-Za-z-]{4,50}$", message = "바코드는 영문/숫자 4~50자여야 합니다")
    private String barcode;

    @NotNull(message = "가격은 필수입니다")
    @DecimalMin(value = "0", message = "가격은 0원 이상이어야 합니다")
    private BigDecimal price;

    @Min(value = 0, message = "재고는 0개 이상이어야 합니다")
    private Integer stock;

    @NotNull(message = "카테고리는 필수입니다")
    private ProductCategory category;

    @Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다")
    private String imageUrl;

    @Size(max = 500, message = "상품 설명은 500자 이하여야 합니다")
    private String description;

    @Size(max = 2000, message = "원재료명은 2000자 이하여야 합니다")
    private String rawIngredientsText;

    private List<@Size(max = 30, message = "알레르기 성분명은 30자 이하여야 합니다") String> allergens = new ArrayList<>();

    private List<@NotNull DietType> diets = new ArrayList<>();
}
