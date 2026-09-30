package org.example.dto.scan;

import org.example.domain.RiskLevel;

import java.util.List;

/**
 * 영수증 스캔 결과 - 영수증에 담긴 상품마다 성분 분석을 돌린 결과 모음.
 *
 * @param source          CLEANEAT_ORDER(CleanEat 주문 영수증) / BARCODES(QR 안의 상품 바코드 목록)
 *                        / RECEIPT_TEXT(영수증 사진의 상품명을 읽어 찾음)
 * @param title           화면 제목 (예: "CleanEat 주문 영수증 · 무첨가 현미 과자 외 2건")
 * @param overallRisk     상품들 중 가장 높은 위험도
 * @param failed          분석하지 못한 항목 (바코드를 찾지 못한 경우 등)
 * @param recommendations 영수증 전체 유해성분/내 알레르기를 피한 대안 상품
 */
public record ReceiptScanResponse(
        String source,
        String title,
        RiskLevel overallRisk,
        int allergyWarningCount,
        List<ReceiptItem> items,
        List<FailedItem> failed,
        List<ProductSummaryResponse> recommendations) {

    /**
     * @param storeProductId CleanEat 판매 상품이면 상품 id (상세 페이지 링크용), 아니면 null
     * @param dietMatch      판매 상품이고 사용자가 식단을 설정했으면 식단 일치 여부, 아니면 null
     * @param matchedText    영수증 사진에서 읽은 글자 (상품명으로 찾은 경우만, 제대로 찾았는지 사용자가 확인용)
     */
    public record ReceiptItem(
            String productName,
            String barcode,
            int quantity,
            Long storeProductId,
            RiskLevel overallRisk,
            List<IngredientMatchResponse> harmfulIngredients,
            List<String> allergenMatches,
            List<String> userAllergyWarnings,
            Boolean dietMatch,
            String matchedText) {
    }

    public record FailedItem(String code, String reason) {
    }
}
