package org.example.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.example.domain.Cart;
import org.example.domain.CartItem;
import org.example.domain.Order;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.domain.StoreListing;
import org.example.domain.User;
import org.example.dto.common.PageResponse;
import org.example.dto.order.CreateOrderRequest;
import org.example.dto.order.OrderItemResponse;
import org.example.dto.order.OrderResponse;
import org.example.dto.order.RefundAccountRequest;
import org.example.dto.payment.PaymentConfirmRequest;
import org.example.exception.PaymentException;
import org.example.exception.ResourceNotFoundException;
import org.example.payment.BankCodes;
import org.example.payment.TossPaymentsClient;
import org.example.payment.TossPaymentsClient.TossPayment;
import org.example.repository.OrderRepository;
import org.example.shipping.Couriers;
import org.example.mail.OrderNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 주문 + 토스페이먼츠 결제 + 배송 흐름 (상태 흐름은 OrderStatus 주석 참고)
 *  1) createFromCart   : 장바구니로 "결제 대기" 주문서를 만든다 (재고/장바구니는 아직 그대로)
 *  2) (프론트) 토스 결제창에서 결제 -> successUrl로 paymentKey/orderId/amount 전달
 *  3) confirmPayment   : 금액/재고 검증 후 토스에 승인 요청 -> 재고 차감, 장바구니 정리, PAID(가상계좌면 AWAITING_DEPOSIT)
 *  - handleDepositWebhook / syncWithToss / adminSyncPayment : 가상계좌 입금 확인 (토스 웹훅 / 10분마다 확인 / 관리자 버튼)
 *                        -> 토스에 결제 상태를 다시 물어서 PAID(입금 안내 메일) / 기한 만료·취소면 CANCELLED(재고 복구)
 *  - cancel            : 고객 취소 (배송 준비 전까지), 결제된 주문은 토스로 환불
 *  - advanceStatus     : 관리자 배송 처리 PAID -> PREPARING -> SHIPPING(발송 안내 메일) -> DELIVERED
 *  - autoCompleteDeliveries : 발송 후 며칠이 지나도 배송중이면 배송 완료로 (관리자가 완료 처리를 잊어도 주문이 멈춰 있지 않게)
 *
 * 재고는 여러 사람이 동시에 결제해도 초과 판매되지 않도록 차감/복구 전에 행 잠금(SELECT ... FOR UPDATE)을 건다.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // 결제창에서 이탈한 대기/실패 주문은 고객 목록에서 숨긴다
    private static final Set<OrderStatus> HIDDEN_STATUSES = EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED);
    private static final Set<OrderStatus> CUSTOMER_CANCELABLE =
            EnumSet.of(OrderStatus.PLACED, OrderStatus.PAID, OrderStatus.AWAITING_DEPOSIT, OrderStatus.PENDING_PAYMENT);
    private static final Set<OrderStatus> ADMIN_CANCELABLE =
            EnumSet.of(OrderStatus.PLACED, OrderStatus.PAID, OrderStatus.AWAITING_DEPOSIT, OrderStatus.PREPARING);
    // 상품 한 줄만 취소 - 고객은 결제 완료까지, 관리자는 배송 준비중까지 (입금 전 가상계좌는 토스가 부분 취소를 지원하지 않음)
    private static final Set<OrderStatus> CUSTOMER_ITEM_CANCELABLE = EnumSet.of(OrderStatus.PAID, OrderStatus.PLACED);
    private static final Set<OrderStatus> ADMIN_ITEM_CANCELABLE =
            EnumSet.of(OrderStatus.PAID, OrderStatus.PLACED, OrderStatus.PREPARING);
    private static final String PENDING_PAYMENT_METHOD = "TOSS";
    // 결제창을 닫고 떠난 주문서는 하루 뒤 정리한다
    private static final long STALE_ORDER_HOURS = 24;
    // 반품 신청 기간 - 배송 완료 후 며칠까지 (전자상거래법 청약철회 기간 7일)
    @Value("${app.orders.return-days:7}")
    private int returnDays = 7;

    private final OrderRepository orderRepository;
    private final CurrentUserService currentUserService;
    private final CartService cartService;
    private final TossPaymentsClient tossPaymentsClient;
    private final EntityManager entityManager;
    private final OrderNotifier orderNotifier;

    @Transactional
    public OrderResponse createFromCart(Authentication authentication, CreateOrderRequest request) {
        User user = currentUserService.getCurrentUser(authentication);
        Cart cart = cartService.findOrCreateCart(user);

        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("장바구니가 비어 있습니다");
        }
        for (CartItem cartItem : cart.getItems()) {
            checkStock(cartItem.getStoreListing(), cartItem.getQuantity());
        }

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING_PAYMENT)
                .recipientName(request.getRecipientName())
                .phone(request.getPhone())
                .address(request.getAddress())
                .requestNote(request.getRequestNote())
                .paymentMethod(PENDING_PAYMENT_METHOD)
                .tossOrderId("CE-" + UUID.randomUUID().toString().replace("-", ""))
                .build();

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem cartItem : cart.getItems()) {
            StoreListing listing = cartItem.getStoreListing();
            orderItems.add(OrderItem.builder()
                    .order(order)
                    .storeListing(listing)
                    .productName(listing.getProduct().getName())
                    .unitPrice(listing.getPrice())
                    .quantity(cartItem.getQuantity())
                    .build());
            total = total.add(listing.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        order.setItems(orderItems);
        order.setTotalAmount(total);
        order.setOrderName(buildOrderName(orderItems));
        orderRepository.save(order);

        return toResponse(order);
    }

    /**
     * 토스 결제 승인. 금액 위변조(프론트에서 amount를 바꿔 보내는 것)를 막기 위해
     * 반드시 서버에 저장된 주문 금액과 비교한 뒤에만 승인 요청한다.
     * 토스가 거절하면 주문을 PAYMENT_FAILED로 남기고 예외를 그대로 던진다 (그래서 PaymentException은 롤백 제외).
     */
    @Transactional(noRollbackFor = PaymentException.class)
    public OrderResponse confirmPayment(Authentication authentication, PaymentConfirmRequest request) {
        User user = currentUserService.getCurrentUser(authentication);
        // 주문 행부터 잠근다 - 결제 버튼 두 번 클릭/새로고침으로 승인 요청이 동시에 오면 두 번째 요청은
        // 첫 번째가 끝날 때까지 기다렸다가 바뀐 상태(PAID)를 읽는다 (안 잠그면 재고가 두 번 빠지거나
        // 토스의 "이미 처리된 결제" 거절로 결제된 주문이 PAYMENT_FAILED로 덮어써진다)
        Order order = findMyOrderForUpdate(user, request.getOrderId());

        // 새로고침 등으로 같은 결제를 다시 승인 요청한 경우 - 이미 처리된 결과를 그대로 돌려준다
        boolean alreadyConfirmed = order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.AWAITING_DEPOSIT;
        if (alreadyConfirmed && request.getPaymentKey().equals(order.getPaymentKey())) {
            return toResponse(order);
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalArgumentException("결제할 수 없는 주문입니다 (현재 상태: " + order.getStatus() + ")");
        }
        if (order.getTotalAmount().compareTo(request.getAmount()) != 0) {
            throw new IllegalArgumentException("결제 금액이 주문 금액과 일치하지 않습니다");
        }

        // 결제창에 있는 동안 다른 사람이 사갔을 수 있다 - 재고 행을 잠근 뒤 확인해야
        // 동시에 결제한 두 사람이 마지막 1개를 둘 다 사는 초과 판매가 생기지 않는다
        lockListings(order);
        for (OrderItem item : order.getItems()) {
            checkStock(item.getStoreListing(), item.getQuantity());
        }

        TossPayment payment;
        try {
            payment = tossPaymentsClient.confirm(request.getPaymentKey(), order.getTossOrderId(), order.getTotalAmount());
        } catch (PaymentException e) {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            order.setFailReason(truncate(e.getMessage()));
            throw e;
        }

        // 가상계좌도 이 시점에 재고를 확보해 둔다 (입금 기한이 지나면 syncWithToss에서 되돌림)
        for (OrderItem item : order.getItems()) {
            StoreListing listing = item.getStoreListing();
            listing.setStock(listing.getStock() - item.getQuantity());
        }
        order.setPaymentKey(payment.paymentKey());
        order.setPaymentMethod(payment.method() != null ? payment.method() : "토스페이먼츠");
        order.setReceiptUrl(payment.receipt() != null ? payment.receipt().url() : null);

        if ("WAITING_FOR_DEPOSIT".equals(payment.status()) && payment.virtualAccount() != null) {
            TossPayment.VirtualAccount va = payment.virtualAccount();
            order.setStatus(OrderStatus.AWAITING_DEPOSIT);
            order.setVirtualAccountBank(BankCodes.nameOf(va.bankCode()));
            order.setVirtualAccountNumber(va.accountNumber());
            order.setVirtualAccountDueDate(parseTime(va.dueDate()));
            order.setVirtualAccountSecret(payment.secret());
        } else {
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(parseTime(payment.approvedAt()));
        }

        removeOrderedItemsFromCart(user, order);
        return toResponse(order);
    }

    /**
     * 토스 웹훅으로 온 가상계좌 입금/상태 변경 알림.
     * secret은 입금 알림에 실려 오는 비밀값 - 발급 때 저장한 값과 다르면 위조 요청으로 보고 무시한다.
     * secret이 맞더라도 요청 내용(상태/금액)은 믿지 않고 토스 API로 다시 조회한 결과만 반영한다.
     */
    @Transactional
    public void handleDepositWebhook(String tossOrderId, String secret) {
        Order order = orderRepository.findForUpdateByTossOrderId(tossOrderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.AWAITING_DEPOSIT) return;
        String expected = order.getVirtualAccountSecret();
        if (secret != null && expected != null && !MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            log.warn("가상계좌 웹훅 secret 불일치 - 무시: {}", tossOrderId);
            return;
        }
        applyTossStatus(order);
    }

    /** 입금 대기 주문을 토스에 직접 확인한다 (웹훅이 못 오는 로컬 환경 등을 위한 주기적 확인) */
    @Transactional
    public void syncWithToss(String tossOrderId) {
        Order order = orderRepository.findForUpdateByTossOrderId(tossOrderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.AWAITING_DEPOSIT) return;
        applyTossStatus(order);
    }

    /** 관리자 "입금 확인" 버튼 - 지금 바로 토스에 확인해서 반영 (아직 입금 전이면 그대로 입금 대기) */
    @Transactional
    public OrderResponse adminSyncPayment(Long orderId) {
        Order found = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다: id=" + orderId));
        if (found.getStatus() != OrderStatus.AWAITING_DEPOSIT || found.getTossOrderId() == null) {
            throw new IllegalArgumentException("입금 대기 중인 주문이 아닙니다 (현재 상태: " + found.getStatus() + ")");
        }
        Order order = orderRepository.findForUpdateByTossOrderId(found.getTossOrderId()).orElse(found);
        if (order.getStatus() == OrderStatus.AWAITING_DEPOSIT) {
            applyTossStatus(order);
        }
        return toResponse(order);
    }

    /** 토스에 결제 상태를 물어서 입금 대기 주문에 반영한다 (호출 전에 주문 행을 잠가 둘 것) */
    private void applyTossStatus(Order order) {
        String tossOrderId = order.getTossOrderId();
        TossPayment payment = tossPaymentsClient.getByOrderId(tossOrderId);
        switch (payment.status()) {
            case "DONE" -> {
                // 토스 금액이 주문 금액과 다르면 자동으로 결제 완료 처리하지 않는다 (관리자가 확인해야 함)
                if (payment.totalAmount() != null && payment.totalAmount().compareTo(order.getTotalAmount()) != 0) {
                    log.error("가상계좌 입금 금액 불일치 - 입금 대기로 둠: {} (주문 {}원, 토스 {}원)",
                            tossOrderId, order.getTotalAmount(), payment.totalAmount());
                    return;
                }
                order.setStatus(OrderStatus.PAID);
                order.setPaidAt(parseTime(payment.approvedAt()));
                orderNotifier.notifyDepositConfirmed(order);
                log.info("가상계좌 입금 확인: {}", tossOrderId);
            }
            case "CANCELED", "PARTIAL_CANCELED", "ABORTED", "EXPIRED" -> {
                boolean expired = order.getVirtualAccountDueDate() != null
                        && order.getVirtualAccountDueDate().isBefore(LocalDateTime.now());
                lockListings(order);
                restoreStock(order);
                order.setStatus(OrderStatus.CANCELLED);
                order.setCancelledAt(LocalDateTime.now());
                order.setFailReason(expired ? "입금 기한이 지나 주문이 취소되었습니다" : "결제가 취소되었습니다");
                if (expired) orderNotifier.notifyDepositExpired(order);
                log.info("가상계좌 주문 취소 ({}): {}", payment.status(), tossOrderId);
            }
            default -> {
                // WAITING_FOR_DEPOSIT 등 - 아직 변화 없음
            }
        }
    }

    @Transactional
    public OrderResponse failPayment(Authentication authentication, String tossOrderId, String reason) {
        User user = currentUserService.getCurrentUser(authentication);
        // 결제 승인과 동시에 들어와도 승인된 주문을 실패로 덮어쓰지 않도록 잠근 뒤 상태를 확인한다
        Order order = findMyOrderForUpdate(user, tossOrderId);
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            order.setFailReason(truncate(reason));
        }
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(Authentication authentication, int page, int size) {
        User user = currentUserService.getCurrentUser(authentication);
        return PageResponse.of(orderRepository.findByUserIdAndStatusInOrderByOrderedAtDescIdDesc(
                user.getId(), visibleStatuses(), PageResponse.request(page, size)), this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOne(Authentication authentication, Long orderId) {
        User user = currentUserService.getCurrentUser(authentication);
        return toResponse(findMyOrder(user, orderId));
    }

    /** 고객 취소 - 배송 준비가 시작되면(PREPARING~) 고객이 직접 취소할 수 없다 */
    @Transactional
    public OrderResponse cancel(Authentication authentication, Long orderId, RefundAccountRequest refundAccount) {
        User user = currentUserService.getCurrentUser(authentication);
        Order order = findMyOrder(user, orderId);
        if (!CUSTOMER_CANCELABLE.contains(order.getStatus())) {
            throw new IllegalArgumentException(order.getStatus() == OrderStatus.CANCELLED
                    ? "이미 취소된 주문입니다"
                    : "배송 준비가 시작되어 직접 취소할 수 없습니다. 고객센터로 문의해주세요");
        }
        cancelOrder(order, "고객 요청 주문 취소", refundAccount);
        return toResponse(order);
    }

    // ---------------- 관리자 ----------------

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> adminList(Set<OrderStatus> statuses, int page, int size) {
        Set<OrderStatus> filter = statuses == null || statuses.isEmpty() ? visibleStatuses() : statuses;
        return PageResponse.of(orderRepository.findByStatusInOrderByOrderedAtDescIdDesc(
                filter, PageResponse.request(page, size)), this::toResponse);
    }

    /**
     * 배송 단계 진행: PAID -> PREPARING -> SHIPPING(택배사/운송장 필수) -> DELIVERED.
     * 단계를 건너뛰거나 되돌릴 수 없다.
     */
    @Transactional
    public OrderResponse advanceStatus(Long orderId, OrderStatus target, String courier, String trackingNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다: id=" + orderId));
        OrderStatus current = order.getStatus();
        boolean allowed = switch (target) {
            case PREPARING -> current == OrderStatus.PAID || current == OrderStatus.PLACED;
            case SHIPPING -> current == OrderStatus.PREPARING;
            case DELIVERED -> current == OrderStatus.SHIPPING;
            default -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException("'" + current + "' 상태에서 '" + target + "'(으)로 바꿀 수 없습니다");
        }
        if (target == OrderStatus.PREPARING) {
            order.setPreparingAt(LocalDateTime.now());
        }
        if (target == OrderStatus.SHIPPING) {
            applyTracking(order, courier, trackingNumber);
            order.setShippedAt(LocalDateTime.now());
        }
        if (target == OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
        }
        order.setStatus(target);
        if (target == OrderStatus.SHIPPING) {
            orderNotifier.notifyShipped(order);
        }
        return toResponse(order);
    }

    /** 운송장 정보 수정 (잘못 입력한 운송장 번호 고치기) - 배송중일 때만. 안내 메일은 다시 보내지 않는다 */
    @Transactional
    public OrderResponse updateTracking(Long orderId, String courier, String trackingNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다: id=" + orderId));
        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new IllegalArgumentException("배송중인 주문만 운송장 정보를 고칠 수 있습니다 (현재 상태: " + order.getStatus() + ")");
        }
        applyTracking(order, courier, trackingNumber);
        return toResponse(order);
    }

    private static void applyTracking(Order order, String courier, String trackingNumber) {
        if (courier == null || courier.isBlank() || trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("택배사와 운송장 번호가 필요합니다");
        }
        order.setCourier(courier.trim());
        order.setTrackingNumber(trackingNumber.trim());
    }

    @Transactional
    public OrderResponse adminCancel(Long orderId, String reason, RefundAccountRequest refundAccount) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다: id=" + orderId));
        if (!ADMIN_CANCELABLE.contains(order.getStatus())) {
            throw new IllegalArgumentException("이미 발송되었거나 취소할 수 없는 주문입니다 (현재 상태: " + order.getStatus() + ")");
        }
        cancelOrder(order, reason == null || reason.isBlank() ? "판매자 사정으로 주문 취소" : reason.trim(), refundAccount);
        return toResponse(order);
    }

    // ---------------- 부분 취소 ----------------

    /** 고객 부분 취소 - 결제 완료 주문의 상품 한 줄 (남은 상품이 그 하나뿐이면 주문 전체 취소) */
    @Transactional
    public OrderResponse cancelItem(Authentication authentication, Long orderId, Long itemId,
                                    RefundAccountRequest refundAccount) {
        User user = currentUserService.getCurrentUser(authentication);
        Order order = orderRepository.findForUpdateById(orderId)
                .filter(o -> o.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다: id=" + orderId));
        if (!CUSTOMER_ITEM_CANCELABLE.contains(order.getStatus())) {
            throw new IllegalArgumentException("배송 준비가 시작되어 상품을 취소할 수 없습니다. 고객센터로 문의해주세요");
        }
        cancelOrderItem(order, itemId, "고객 요청 부분 취소", refundAccount);
        return toResponse(order);
    }

    /** 관리자 부분 취소 - 배송 준비중까지 (품절/파손 등으로 일부만 보낼 수 없을 때) */
    @Transactional
    public OrderResponse adminCancelItem(Long orderId, Long itemId, String reason, RefundAccountRequest refundAccount) {
        Order order = orderRepository.findForUpdateById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다: id=" + orderId));
        if (!ADMIN_ITEM_CANCELABLE.contains(order.getStatus())) {
            throw new IllegalArgumentException("이미 발송되었거나 취소할 수 없는 주문입니다 (현재 상태: " + order.getStatus() + ")");
        }
        cancelOrderItem(order, itemId,
                reason == null || reason.isBlank() ? "판매자 사정으로 부분 취소" : reason.trim(), refundAccount);
        return toResponse(order);
    }

    /**
     * 상품 한 줄 취소: 토스로 그 금액만 환불 -> 그 상품 재고만 복구. 환불이 먼저 성공해야 바꾼다.
     * 남은 상품이 이것 하나면 주문 전체 취소와 같다 (남은 금액 전부 환불, 주문 상태 CANCELLED).
     */
    private void cancelOrderItem(Order order, Long itemId, String reason, RefundAccountRequest refundAccount) {
        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("주문 상품을 찾을 수 없습니다: id=" + itemId));
        if (item.isCancelled()) {
            throw new IllegalArgumentException("이미 취소된 상품입니다");
        }
        long remaining = order.getItems().stream().filter(i -> !i.isCancelled()).count();
        if (remaining == 1) {
            cancelOrder(order, reason, refundAccount);
            return;
        }
        BigDecimal amount = item.lineAmount();
        if (order.getPaymentKey() != null) {
            tossPaymentsClient.cancelPartial(order.getPaymentKey(), truncate(reason + ": " + item.getProductName()), amount,
                    needsRefundAccount(order) ? toRefundAccount(refundAccount) : null, "item-" + item.getId());
        }
        StoreListing listing = item.getStoreListing();
        if (listing != null) {
            if (entityManager.contains(listing)) entityManager.refresh(listing, LockModeType.PESSIMISTIC_WRITE);
            listing.setStock(listing.getStock() + item.getQuantity());
        }
        item.setCancelledAt(LocalDateTime.now());
        order.setCancelledAmount(order.cancelledAmountOrZero().add(amount));
    }

    // ---------------- 반품 ----------------

    /**
     * 고객 반품 신청: 배송 완료 후 returnDays일 안에, 한 주문에 한 번 (거절되면 다시 신청할 수 없다).
     * 가상계좌로 결제한 주문은 환불 계좌를 함께 받아 둔다 (승인 때 토스 환불에 쓰고 바로 지운다).
     */
    @Transactional
    public OrderResponse requestReturn(Authentication authentication, Long orderId, String reason,
                                       RefundAccountRequest refundAccount) {
        User user = currentUserService.getCurrentUser(authentication);
        Order order = findMyOrder(user, orderId);
        if (order.getStatus() == OrderStatus.RETURN_REQUESTED) {
            throw new IllegalArgumentException("이미 반품을 신청한 주문입니다");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("배송이 완료된 주문만 반품을 신청할 수 있습니다 (현재 상태: " + order.getStatus() + ")");
        }
        if (order.getReturnRejectedAt() != null) {
            throw new IllegalArgumentException("반품이 거절된 주문입니다. 다시 요청하시려면 고객센터로 문의해주세요");
        }
        if (LocalDateTime.now().isAfter(returnDeadline(order))) {
            throw new IllegalArgumentException("배송 완료 후 " + returnDays + "일이 지나 반품 신청 기간이 끝났습니다. 고객센터로 문의해주세요");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("반품 사유를 입력해주세요");
        }
        if (needsRefundAccount(order)) {
            TossPaymentsClient.RefundAccount account = toRefundAccount(refundAccount);
            order.setReturnRefundBank(account.bank());
            order.setReturnRefundAccount(account.accountNumber());
            order.setReturnRefundHolder(account.holderName());
        }
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        order.setReturnReason(truncate(reason.trim()));
        order.setReturnRequestedAt(LocalDateTime.now());
        return toResponse(order);
    }

    /** 고객 반품 신청 철회 - 관리자가 처리하기 전까지만 (다시 신청할 수 있다) */
    @Transactional
    public OrderResponse withdrawReturn(Authentication authentication, Long orderId) {
        User user = currentUserService.getCurrentUser(authentication);
        Order order = findMyOrder(user, orderId);
        if (order.getStatus() != OrderStatus.RETURN_REQUESTED) {
            throw new IllegalArgumentException("반품 신청 중인 주문이 아닙니다 (현재 상태: " + order.getStatus() + ")");
        }
        order.setStatus(OrderStatus.DELIVERED);
        order.setReturnReason(null);
        order.setReturnRequestedAt(null);
        clearReturnRefundAccount(order);
        return toResponse(order);
    }

    /**
     * 관리자 반품 승인: 토스로 전액 환불 -> 재고 복구 -> RETURNED.
     * 환불이 먼저 성공해야 재고/상태를 바꾼다 (토스가 거절하면 예외로 빠져나가 반품 신청 상태 그대로).
     */
    @Transactional
    public OrderResponse adminApproveReturn(Long orderId) {
        Order order = orderRepository.findForUpdateById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다: id=" + orderId));
        if (order.getStatus() != OrderStatus.RETURN_REQUESTED) {
            throw new IllegalArgumentException("반품 신청 중인 주문이 아닙니다 (현재 상태: " + order.getStatus() + ")");
        }
        if (order.getPaymentKey() != null) {
            TossPaymentsClient.RefundAccount refundAccount = null;
            if (needsRefundAccount(order)) {
                if (order.getReturnRefundAccount() == null) {
                    throw new IllegalArgumentException("가상계좌 결제 주문인데 환불 계좌가 없습니다. 고객에게 다시 신청하도록 안내해주세요");
                }
                refundAccount = new TossPaymentsClient.RefundAccount(order.getReturnRefundBank(),
                        order.getReturnRefundAccount(), order.getReturnRefundHolder());
            }
            tossPaymentsClient.cancel(order.getPaymentKey(), truncate("반품: " + order.getReturnReason()), refundAccount);
        }
        lockListings(order);
        restoreStock(order);
        order.setStatus(OrderStatus.RETURNED);
        order.setReturnedAt(LocalDateTime.now());
        clearReturnRefundAccount(order);
        orderNotifier.notifyReturnApproved(order);
        return toResponse(order);
    }

    /** 관리자 반품 거절: 배송 완료로 되돌리고 사유를 남긴다 (고객 주문 내역에 보임, 다시 신청 불가) */
    @Transactional
    public OrderResponse adminRejectReturn(Long orderId, String reason) {
        Order order = orderRepository.findForUpdateById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다: id=" + orderId));
        if (order.getStatus() != OrderStatus.RETURN_REQUESTED) {
            throw new IllegalArgumentException("반품 신청 중인 주문이 아닙니다 (현재 상태: " + order.getStatus() + ")");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("거절 사유를 입력해주세요");
        }
        order.setStatus(OrderStatus.DELIVERED);
        order.setReturnRejectReason(truncate(reason.trim()));
        order.setReturnRejectedAt(LocalDateTime.now());
        clearReturnRefundAccount(order);
        orderNotifier.notifyReturnRejected(order);
        return toResponse(order);
    }

    private LocalDateTime returnDeadline(Order order) {
        LocalDateTime delivered = order.getDeliveredAt() != null ? order.getDeliveredAt() : order.getShippedAt();
        return delivered == null ? null : delivered.plusDays(returnDays);
    }

    private boolean isReturnable(Order order) {
        LocalDateTime deadline = returnDeadline(order);
        return order.getStatus() == OrderStatus.DELIVERED && order.getReturnRejectedAt() == null
                && deadline != null && !LocalDateTime.now().isAfter(deadline);
    }

    private static void clearReturnRefundAccount(Order order) {
        order.setReturnRefundBank(null);
        order.setReturnRefundAccount(null);
        order.setReturnRefundHolder(null);
    }

    // 관리자 승인 화면용 - "신한은행 ****6789 홍길동" (계좌번호는 끝 4자리만)
    private static String refundAccountSummary(Order order) {
        String account = order.getReturnRefundAccount();
        if (account == null) return null;
        String tail = account.length() > 4 ? account.substring(account.length() - 4) : account;
        return BankCodes.nameOf(order.getReturnRefundBank()) + " ****" + tail + " " + order.getReturnRefundHolder();
    }

    // ---------------- 정리 작업 (스케줄러) ----------------

    /** 결제창을 닫고 떠나 하루 넘게 남아 있는 결제대기/결제실패 주문서를 지운다 (실제 결제가 없던 주문) */
    @Transactional
    public int deleteStaleOrders() {
        LocalDateTime before = LocalDateTime.now().minusHours(STALE_ORDER_HOURS);
        List<Order> stale = orderRepository.findByStatusInAndOrderedAtBefore(HIDDEN_STATUSES, before);
        orderRepository.deleteAll(stale);
        return stale.size();
    }

    /** 발송 후 days일이 지나도 배송중인 주문을 배송 완료로 바꾼다 (택배는 보통 1~3일이면 도착) */
    @Transactional
    public int autoCompleteDeliveries(int days) {
        List<Order> stale = orderRepository.findByStatusAndShippedAtBefore(
                OrderStatus.SHIPPING, LocalDateTime.now().minusDays(days));
        LocalDateTime now = LocalDateTime.now();
        for (Order order : stale) {
            order.setStatus(OrderStatus.DELIVERED);
            order.setDeliveredAt(now);
        }
        return stale.size();
    }

    @Transactional(readOnly = true)
    public List<String> awaitingDepositOrderIds() {
        return orderRepository.findByStatus(OrderStatus.AWAITING_DEPOSIT).stream().map(Order::getTossOrderId).toList();
    }

    // ---------------- 내부 ----------------

    private static Set<OrderStatus> visibleStatuses() {
        return EnumSet.complementOf(EnumSet.copyOf(HIDDEN_STATUSES));
    }

    private void cancelOrder(Order order, String reason, RefundAccountRequest refundAccount) {
        OrderStatus status = order.getStatus();
        boolean stockTaken = status == OrderStatus.PLACED || status == OrderStatus.PAID
                || status == OrderStatus.AWAITING_DEPOSIT || status == OrderStatus.PREPARING;
        boolean paidOnline = order.getPaymentKey() != null
                && (status == OrderStatus.PAID || status == OrderStatus.AWAITING_DEPOSIT || status == OrderStatus.PREPARING);

        // 환불(또는 입금 전 가상계좌 취소)이 먼저 성공해야 재고/상태를 바꾼다 - 토스가 거절하면 예외로 빠져나가 아무것도 바뀌지 않음
        if (paidOnline) {
            tossPaymentsClient.cancel(order.getPaymentKey(), reason,
                    needsRefundAccount(order) ? toRefundAccount(refundAccount) : null);
        }
        if (stockTaken) {
            lockListings(order);
            restoreStock(order);
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setFailReason(truncate(reason));
    }

    /**
     * 입금까지 끝난 가상계좌 주문인지 - 토스가 원래 결제수단으로 돌려줄 수 없어서 환불 계좌가 필요하다.
     * (입금 전 AWAITING_DEPOSIT 취소는 돌려줄 돈이 없으므로 필요 없음)
     */
    private static boolean needsRefundAccount(Order order) {
        return order.getVirtualAccountNumber() != null && order.getStatus() != OrderStatus.AWAITING_DEPOSIT;
    }

    private static TossPaymentsClient.RefundAccount toRefundAccount(RefundAccountRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("가상계좌로 결제한 주문은 환불 받을 계좌를 입력해야 취소할 수 있습니다");
        }
        if (!BankCodes.isKnown(request.getBankCode())) {
            throw new IllegalArgumentException("지원하지 않는 은행입니다: " + request.getBankCode());
        }
        return new TossPaymentsClient.RefundAccount(request.getBankCode(),
                request.getAccountNumber().replace("-", ""), request.getHolderName().trim());
    }

    /** 주문 상품들의 재고 행을 id 순서대로 잠그고 최신 값으로 다시 읽는다 (id 순서 고정 = 교착 상태 방지) */
    private void lockListings(Order order) {
        order.getItems().stream()
                .map(OrderItem::getStoreListing)
                .filter(listing -> listing != null && entityManager.contains(listing))
                .distinct()
                .sorted(Comparator.comparing(StoreListing::getId))
                .forEach(listing -> entityManager.refresh(listing, LockModeType.PESSIMISTIC_WRITE));
    }

    private Order findMyOrder(User user, Long orderId) {
        return orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다: id=" + orderId));
    }

    private Order findMyOrderForUpdate(User user, String tossOrderId) {
        return orderRepository.findForUpdateByTossOrderId(tossOrderId)
                .filter(order -> order.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("주문을 찾을 수 없습니다: " + tossOrderId));
    }

    private static void checkStock(StoreListing listing, int quantity) {
        if (listing == null) {
            throw new IllegalArgumentException("판매가 중지된 상품이 포함되어 있습니다");
        }
        if (quantity > listing.getStock()) {
            throw new IllegalArgumentException(
                    listing.getProduct().getName() + "의 재고가 부족합니다: 남은 재고 " + listing.getStock() + "개");
        }
    }

    private static void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            // 부분 취소된 상품은 그때 이미 재고를 되돌렸다
            if (item.getStoreListing() != null && !item.isCancelled()) {
                item.getStoreListing().setStock(item.getStoreListing().getStock() + item.getQuantity());
            }
        }
    }

    // 결제하는 사이 장바구니가 바뀌었을 수 있으므로 통째로 비우지 않고 주문한 상품만 뺀다
    private void removeOrderedItemsFromCart(User user, Order order) {
        Cart cart = cartService.findOrCreateCart(user);
        Set<Long> orderedListingIds = new HashSet<>();
        for (OrderItem item : order.getItems()) {
            if (item.getStoreListing() != null) orderedListingIds.add(item.getStoreListing().getId());
        }
        cart.getItems().removeIf(cartItem -> orderedListingIds.contains(cartItem.getStoreListing().getId()));
    }

    private static String truncate(String text) {
        return text != null && text.length() > 300 ? text.substring(0, 300) : text;
    }

    private static String buildOrderName(List<OrderItem> items) {
        String first = items.get(0).getProductName();
        String name = items.size() > 1 ? first + " 외 " + (items.size() - 1) + "건" : first;
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private static LocalDateTime parseTime(String isoTime) {
        if (isoTime == null) return LocalDateTime.now();
        try {
            return OffsetDateTime.parse(isoTime).toLocalDateTime();
        } catch (RuntimeException e) {
            return LocalDateTime.now();
        }
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(item.getId(), item.getProductName(), item.getUnitPrice(),
                        item.getQuantity(), item.isCancelled()))
                .toList();
        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .items(items)
                .totalAmount(order.getTotalAmount())
                .cancelledAmount(order.cancelledAmountOrZero())
                .netAmount(order.netAmount())
                .recipientName(order.getRecipientName())
                .phone(order.getPhone())
                .address(order.getAddress())
                .requestNote(order.getRequestNote())
                .paymentMethod(order.getPaymentMethod())
                .orderedAt(order.getOrderedAt())
                .tossOrderId(order.getTossOrderId())
                .orderName(order.getOrderName())
                .paidAt(order.getPaidAt())
                .receiptUrl(order.getReceiptUrl())
                .failReason(order.getFailReason())
                .virtualAccountBank(order.getVirtualAccountBank())
                .virtualAccountNumber(order.getVirtualAccountNumber())
                .virtualAccountDueDate(order.getVirtualAccountDueDate())
                .courier(order.getCourier())
                .trackingNumber(order.getTrackingNumber())
                .trackingUrl(Couriers.trackingUrl(order.getCourier(), order.getTrackingNumber()))
                .preparingAt(order.getPreparingAt())
                .shippedAt(order.getShippedAt())
                .deliveredAt(order.getDeliveredAt())
                .cancelledAt(order.getCancelledAt())
                .cancelable(CUSTOMER_CANCELABLE.contains(order.getStatus()))
                .itemCancelable(CUSTOMER_ITEM_CANCELABLE.contains(order.getStatus())
                        && order.getItems().stream().filter(i -> !i.isCancelled()).count() > 1)
                .returnable(isReturnable(order))
                .returnDeadline(order.getStatus() == OrderStatus.DELIVERED ? returnDeadline(order) : null)
                .returnReason(order.getReturnReason())
                .returnRequestedAt(order.getReturnRequestedAt())
                .returnRejectReason(order.getReturnRejectReason())
                .returnRejectedAt(order.getReturnRejectedAt())
                .returnedAt(order.getReturnedAt())
                .returnRefundAccountSummary(refundAccountSummary(order))
                .customerNickname(order.getUser() != null ? order.getUser().getNickname() : null)
                .build();
    }
}
