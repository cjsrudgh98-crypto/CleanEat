package org.example.dto.ingredient;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.example.domain.RiskLevel;

import java.util.ArrayList;
import java.util.List;

/** 관리자: 유해성분 사전 추가/수정 */
@Getter
@Setter
public class IngredientRequest {

    @NotBlank(message = "성분 이름은 필수입니다")
    @Size(max = 100, message = "성분 이름은 100자 이하여야 합니다")
    private String name;

    @NotNull(message = "위험도는 필수입니다")
    private RiskLevel riskLevel;

    @Size(max = 30, message = "분류는 30자 이하여야 합니다")
    private String category;

    @Size(max = 1000, message = "설명은 1000자 이하여야 합니다")
    private String description;

    // 영어 이름, E-번호, 다른 한국어 표기 (비어 있는 줄은 무시)
    @Size(max = 50, message = "별칭은 50개까지 등록할 수 있습니다")
    private List<@Size(max = 100, message = "별칭은 100자 이하여야 합니다") String> aliases = new ArrayList<>();
}
