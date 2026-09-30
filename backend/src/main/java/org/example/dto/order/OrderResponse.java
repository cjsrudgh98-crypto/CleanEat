package org.example.dto.order;

import lombok.Builder;
import lombok.Getter;
import org.example.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class OrderResponse {
    private Long id;
    private OrderStatus status;
    private List<OrderItemResponse> items;
    private BigDecimal totalAmount;
    // 부분 취소로 환불된 금액 합계 / 실제 결제 금액 (totalAmount - cancelledAmount)
    private BigDecimal cancelledAmount;
    private BigDecimal netAmount;
    private String recipientName;
    private String phone;
    private String address;
    private String requestNote;
    private String paymentMethod;
    private LocalDateTime orderedAt;
    // 토스 결제창에 넘길 주문번호/주문명
    private String tossOrderId;
    private String orderName;
    private LocalDateTime paidAt;
    private String receiptUrl;
    private String failReason;
    // 가상계좌 입금 안내 (입금 대기 중일 때만)
    private String virtualAccountBank;
    private String virtualAccountNumber;
    private LocalDateTime virtualAccountDueDate;
    // 배송
    private String courier;
    private String trackingNumber;
    // 택배사 배송 조회 페이지 (목록에 없는 택배사면 null)
    private String trackingUrl;
    private LocalDateTime preparingAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    // 고객이 직접 취소할 수 있는 상태인지 (프론트 취소 버튼 노출용)
    private boolean cancelable;
    // 고객이 상품 한 줄만 취소할 수 있는지 (결제 완료 + 남은 상품 2개 이상 - 하나 남으면 주문 취소로)
    private boolean itemCancelable;
    // --- 반품 ---
    // 지금 반품 신청할 수 있는지 (배송 완료 + 기간 안 + 거절된 적 없음) / 신청 마감 시각 (배송 완료 주문만)
    private boolean returnable;
    private LocalDateTime returnDeadline;
    private String returnReason;
    private LocalDateTime returnRequestedAt;
    private String returnRejectReason;
    private LocalDateTime returnRejectedAt;
    private LocalDateTime returnedAt;
    // 가상계좌 반품의 환불 계좌 요약 (예: "신한은행 ****6789 홍길동") - 관리자 승인 확인용, 계좌번호는 가린다
    private String returnRefundAccountSummary;
    // 관리자 화면용 주문자 아이디/닉네임 (고객 본인 조회 시에도 채워지지만 본인 정보)
    private String customerNickname;
}
