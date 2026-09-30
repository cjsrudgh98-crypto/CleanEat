package org.example.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 기간별 매출. 금액은 부분 취소를 뺀 실제 결제 금액, 결제일(paidAt) 기준.
 * 전체 취소/반품 완료 주문은 매출에서 빠진다 (반품 신청 중은 아직 매출).
 *
 * @param unit   "day"(7d/30d) 또는 "month"(12m)
 * @param series 기간 안의 모든 칸 (매출이 없는 날/달도 0으로) - 오래된 순
 */
public record SalesReportResponse(
        String period,
        String unit,
        LocalDate from,
        LocalDate to,
        BigDecimal totalSales,
        long totalOrders,
        BigDecimal averageOrderAmount,
        List<Point> series,
        List<TopProduct> topProducts) {

    /** @param label 화면 표시용 (일: "9/30", 월: "2026.09") / start 그 칸의 첫날 */
    public record Point(String label, LocalDate start, BigDecimal sales, long orders) {
    }

    /** 부분 취소된 상품은 빼고 센다 */
    public record TopProduct(String productName, long quantity, BigDecimal sales) {
    }
}
