package org.example.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(nullable = false, precision = 10, scale = 0)
    private BigDecimal totalAmount;

    // 부분 취소로 환불한 금액 합계 (상품 줄 단위). 컬럼 추가 전 주문은 null -> 0
    @Column(precision = 10, scale = 0)
    private BigDecimal cancelledAmount;

    @Column(nullable = false, length = 50)
    private String recipientName;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(length = 300)
    private String requestNote;

    // 결제 전에는 "TOSS", 승인 후에는 토스가 알려준 실제 결제수단(예: "카드", "간편결제")
    @Column(nullable = false, length = 20)
    private String paymentMethod;

    // 토스 결제창에 넘기는 주문번호 (6~64자 영문/숫자/-/_). 내부 id를 그대로 노출하지 않으려고 따로 둔다
    @Column(unique = true, length = 64)
    private String tossOrderId;

    // 결제창에 표시되는 주문명 (예: "무첨가 현미 과자 외 2건")
    @Column(length = 100)
    private String orderName;

    // 토스가 발급한 결제 키 - 결제 취소(환불) 시 필요
    @Column(length = 200)
    private String paymentKey;

    private LocalDateTime paidAt;

    @Column(length = 500)
    private String receiptUrl;

    // 결제 실패/취소 사유
    @Column(length = 300)
    private String failReason;

    // --- 가상계좌 (입금 대기 중일 때 고객에게 보여줄 정보) ---
    @Column(length = 30)
    private String virtualAccountBank;

    @Column(length = 50)
    private String virtualAccountNumber;

    private LocalDateTime virtualAccountDueDate;

    // 토스가 가상계좌 발급 때 준 비밀값 - 입금 웹훅에 같은 값이 와야 진짜 토스 요청으로 본다 (고객에게 노출하지 않음)
    @Column(length = 100)
    private String virtualAccountSecret;

    // --- 배송 (관리자가 입력) ---
    // 배송 준비를 시작한 시각 (이때부터 고객이 직접 취소할 수 없음)
    private LocalDateTime preparingAt;

    @Column(length = 30)
    private String courier;

    @Column(length = 50)
    private String trackingNumber;

    private LocalDateTime shippedAt;

    private LocalDateTime deliveredAt;

    private LocalDateTime cancelledAt;

    // --- 반품 ---
    // 고객이 적은 반품 사유 (관리자 화면과 토스 환불 사유에 쓰인다)
    @Column(length = 300)
    private String returnReason;

    private LocalDateTime returnRequestedAt;

    // 관리자가 거절한 사유 (고객 주문 내역에 보인다). 거절된 주문은 다시 신청할 수 없다
    @Column(length = 300)
    private String returnRejectReason;

    private LocalDateTime returnRejectedAt;

    // 반품 승인(환불 완료) 시각
    private LocalDateTime returnedAt;

    // 가상계좌로 결제한 주문의 반품 환불 계좌 (카드와 달리 원래 결제수단으로 돌려줄 수 없어서 신청 때 받는다).
    // 승인(환불)이나 거절, 철회로 필요 없어지면 바로 지운다
    @Column(length = 2)
    private String returnRefundBank;

    @Column(length = 30)
    private String returnRefundAccount;

    @Column(length = 20)
    private String returnRefundHolder;

    @Builder.Default
    private LocalDateTime orderedAt = LocalDateTime.now();

    public BigDecimal cancelledAmountOrZero() {
        return cancelledAmount == null ? BigDecimal.ZERO : cancelledAmount;
    }

    /** 부분 취소를 뺀 실제 결제 금액 (매출/환불 안내에 쓴다) */
    public BigDecimal netAmount() {
        return totalAmount.subtract(cancelledAmountOrZero());
    }
}
