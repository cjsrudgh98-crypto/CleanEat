package org.example.domain;

/**
 * 주문 상태 흐름
 *   PENDING_PAYMENT ─┬─> PAID ─> PREPARING ─> SHIPPING ─> DELIVERED ─> RETURN_REQUESTED ─┬─> RETURNED (환불)
 *                    ├─> AWAITING_DEPOSIT ─> PAID ...   (가상계좌: 입금 확인되면 PAID)       └─> DELIVERED (거절/철회)
 *                    └─> PAYMENT_FAILED
 *   취소(CANCELLED): 고객은 PAID/AWAITING_DEPOSIT까지, 관리자는 PREPARING까지 (환불 포함)
 *   반품: 배송 완료 후 app.orders.return-days일(기본 7일) 안에 고객이 신청 -> 관리자 승인 시 전액 환불 + 재고 복구
 */
public enum OrderStatus {
    // 결제 연동 전에 만들어진 주문 (결제 없이 바로 접수됨) - 기존 데이터 호환용
    PLACED,
    // 주문서는 만들어졌고 토스 결제창에서 결제를 기다리는 중 (재고/장바구니는 아직 그대로)
    PENDING_PAYMENT,
    // 가상계좌 발급 완료, 입금 기다리는 중 (재고는 이미 확보)
    AWAITING_DEPOSIT,
    // 결제 완료 - 이 시점에 재고 차감 + 장바구니 비움
    PAID,
    // 배송 준비중 (관리자가 변경) - 이때부터 고객이 직접 취소할 수 없음
    PREPARING,
    // 배송중 (운송장 번호 있음)
    SHIPPING,
    DELIVERED,
    // 고객이 반품을 신청함 - 관리자가 승인(환불)하거나 거절할 때까지
    RETURN_REQUESTED,
    // 반품 승인 - 환불과 재고 복구까지 끝남
    RETURNED,
    // 결제창에서 실패/취소
    PAYMENT_FAILED,
    CANCELLED;

    /** 결제가 끝나서 실제로 판매된 주문인지 (리뷰 작성 자격, 통계 등에 사용) - 반품 신청 중은 아직 판매된 것으로 본다 */
    public boolean isPurchased() {
        return this == PAID || this == PREPARING || this == SHIPPING || this == DELIVERED || this == PLACED
                || this == RETURN_REQUESTED;
    }

    /** 아직 처리가 끝나지 않은 진행 중 주문인지 (회원 탈퇴 제한 등에 사용) - 반품 처리 중도 포함 */
    public boolean isInProgress() {
        return this == AWAITING_DEPOSIT || this == PAID || this == PREPARING || this == SHIPPING || this == PLACED
                || this == RETURN_REQUESTED;
    }
}
