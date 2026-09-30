package org.example.dto.scan;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.domain.RiskLevel;

import java.util.List;

@Getter
@AllArgsConstructor
public class ScanResultResponse {
    private String productName;
    private String barcode;
    private RiskLevel overallRisk;
    private List<IngredientMatchResponse> harmfulIngredients;
    // 이 제품에 들어 있는 알레르기 유발 성분 전체
    private List<String> allergenMatches;
    // 그중 로그인 사용자가 마이페이지에 등록한 알레르기에 해당하는 것 (비로그인/해당 없음이면 빈 목록)
    private List<String> userAllergyWarnings;
    private List<ProductSummaryResponse> recommendations;
}
