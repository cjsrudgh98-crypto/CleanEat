package org.example.dto.admin;

import java.math.BigDecimal;

// 관리자 첫 화면 요약 - 처리해야 할 일 위주
public record AdminSummaryResponse(
        long paid,              // 결제 완료, 배송 준비 시작 전 (처리 필요)
        long preparing,         // 배송 준비중 (운송장 입력 필요)
        long shipping,          // 배송중
        long awaitingDeposit,   // 가상계좌 입금 대기
        long returnRequested,   // 반품 신청 (승인/거절 필요)
        long lowStock,          // 재고 LOW_STOCK_THRESHOLD개 이하 (품절 제외)
        long soldOut,
        int lowStockThreshold,
        BigDecimal todaySales,  // 오늘 결제된 금액 (이후 취소된 주문 제외)
        long todayOrders) {
}
