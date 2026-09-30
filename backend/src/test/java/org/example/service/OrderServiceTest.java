package org.example.service;

import org.example.domain.Cart;
import org.example.domain.CartItem;
import org.example.domain.Order;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.domain.Product;
import org.example.domain.StoreListing;
import org.example.domain.User;
import org.example.dto.order.CreateOrderRequest;
import org.example.dto.order.OrderResponse;
import org.example.dto.order.RefundAccountRequest;
import org.example.dto.payment.PaymentConfirmRequest;
import org.example.exception.PaymentException;
import org.example.exception.ResourceNotFoundException;
import org.example.payment.TossPaymentsClient;
import org.example.payment.TossPaymentsClient.TossPayment;
import org.example.repository.OrderRepository;
import org.example.mail.OrderNotifier;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SliceImpl;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private CartService cartService;

    @Mock
    private TossPaymentsClient tossPaymentsClient;

    @Mock
    private OrderNotifier orderNotifier;

    // 재고 행 잠금용 - 목(mock)에서는 contains()가 false라 잠금/새로고침 없이 지나간다
    @Mock
    private EntityManager entityManager;

    @Mock
    private Authentication authentication;

    private OrderService orderService;

    private User user;
    private Cart cart;
    private StoreListing listing;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, currentUserService, cartService, tossPaymentsClient, entityManager,
                orderNotifier);

        user = User.builder().id(1L).username("cleaneat_user").build();
        Product product = Product.builder().id(10L).name("무첨가 현미 과자").build();
        listing = StoreListing.builder().id(100L).product(product).price(new BigDecimal(4500)).stock(5).build();
        cart = Cart.builder().id(1000L).user(user).build();

        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
    }

    private CreateOrderRequest request() {
        CreateOrderRequest req = new CreateOrderRequest();
        req.setRecipientName("홍길동");
        req.setPhone("010-1234-5678");
        req.setAddress("서울시 어딘가 123");
        return req;
    }

    // 결제 대기 상태의 주문 (현미 과자 2개 = 9000원)
    private Order pendingOrder() {
        OrderItem item = OrderItem.builder()
                .id(1L).storeListing(listing).productName("무첨가 현미 과자").unitPrice(new BigDecimal(4500)).quantity(2).build();
        Order order = Order.builder().id(1L).user(user).totalAmount(new BigDecimal(9000))
                .recipientName("홍길동").phone("010").address("주소").paymentMethod("TOSS")
                .tossOrderId("CE-test-order").status(OrderStatus.PENDING_PAYMENT).build();
        item.setOrder(order);
        order.getItems().add(item);
        return order;
    }

    private PaymentConfirmRequest confirmRequest(BigDecimal amount) {
        PaymentConfirmRequest req = new PaymentConfirmRequest();
        req.setPaymentKey("pay_key_123");
        req.setOrderId("CE-test-order");
        req.setAmount(amount);
        return req;
    }

    @Test
    void 주문서를_만들면_결제대기_상태이고_재고와_장바구니는_그대로다() {
        cart.getItems().add(CartItem.builder().id(1L).cart(cart).storeListing(listing).quantity(2).build());
        when(cartService.findOrCreateCart(user)).thenReturn(cart);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(500L);
            return order;
        });

        OrderResponse response = orderService.createFromCart(authentication, request());

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal(9000));
        assertThat(response.getTossOrderId()).startsWith("CE-").hasSizeBetween(6, 64);
        assertThat(response.getOrderName()).isEqualTo("무첨가 현미 과자");
        assertThat(listing.getStock()).isEqualTo(5);
        assertThat(cart.getItems()).hasSize(1);
    }

    @Test
    void 장바구니가_비어있으면_예외를_던진다() {
        when(cartService.findOrCreateCart(user)).thenReturn(cart);

        assertThatThrownBy(() -> orderService.createFromCart(authentication, request()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 재고보다_많으면_예외를_던지고_저장하지_않는다() {
        listing.setStock(1);
        cart.getItems().add(CartItem.builder().id(1L).cart(cart).storeListing(listing).quantity(2).build());
        when(cartService.findOrCreateCart(user)).thenReturn(cart);

        assertThatThrownBy(() -> orderService.createFromCart(authentication, request()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void 결제가_승인되면_재고를_차감하고_주문한_상품만_장바구니에서_뺀다() {
        Order order = pendingOrder();
        when(orderRepository.findForUpdateByTossOrderId("CE-test-order")).thenReturn(Optional.of(order));
        when(tossPaymentsClient.confirm("pay_key_123", "CE-test-order", new BigDecimal(9000)))
                .thenReturn(new TossPayment("pay_key_123", "CE-test-order", "DONE", "카드", new BigDecimal(9000),
                        "2026-09-29T15:00:00+09:00", new TossPayment.Receipt("https://receipt.example")));

        StoreListing otherListing = StoreListing.builder().id(200L).price(BigDecimal.ONE).stock(10).build();
        cart.getItems().add(CartItem.builder().id(1L).cart(cart).storeListing(listing).quantity(2).build());
        cart.getItems().add(CartItem.builder().id(2L).cart(cart).storeListing(otherListing).quantity(1).build());
        when(cartService.findOrCreateCart(user)).thenReturn(cart);

        OrderResponse response = orderService.confirmPayment(authentication, confirmRequest(new BigDecimal(9000)));

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(response.getPaymentMethod()).isEqualTo("카드");
        assertThat(response.getReceiptUrl()).isEqualTo("https://receipt.example");
        assertThat(listing.getStock()).isEqualTo(3);
        assertThat(cart.getItems()).extracting(CartItem::getId).containsExactly(2L);
    }

    @Test
    void 결제금액이_주문금액과_다르면_토스에_승인요청하지_않는다() {
        when(orderRepository.findForUpdateByTossOrderId("CE-test-order")).thenReturn(Optional.of(pendingOrder()));

        assertThatThrownBy(() -> orderService.confirmPayment(authentication, confirmRequest(new BigDecimal(100))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), any());
        assertThat(listing.getStock()).isEqualTo(5);
    }

    @Test
    void 결제창에_있는_동안_재고가_모자라지면_승인요청하지_않는다() {
        when(orderRepository.findForUpdateByTossOrderId("CE-test-order")).thenReturn(Optional.of(pendingOrder()));
        listing.setStock(1);

        assertThatThrownBy(() -> orderService.confirmPayment(authentication, confirmRequest(new BigDecimal(9000))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), any());
    }

    @Test
    void 토스가_승인을_거절하면_결제실패로_남기고_재고는_그대로다() {
        Order order = pendingOrder();
        when(orderRepository.findForUpdateByTossOrderId("CE-test-order")).thenReturn(Optional.of(order));
        when(tossPaymentsClient.confirm(anyString(), anyString(), any()))
                .thenThrow(new PaymentException("REJECT_CARD_COMPANY", "카드사에서 거절했습니다"));

        assertThatThrownBy(() -> orderService.confirmPayment(authentication, confirmRequest(new BigDecimal(9000))))
                .isInstanceOf(PaymentException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getFailReason()).isEqualTo("카드사에서 거절했습니다");
        assertThat(listing.getStock()).isEqualTo(5);
    }

    @Test
    void 이미_승인된_결제를_다시_요청하면_토스를_다시_부르지_않고_결과를_돌려준다() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.PAID);
        order.setPaymentKey("pay_key_123");
        when(orderRepository.findForUpdateByTossOrderId("CE-test-order")).thenReturn(Optional.of(order));

        OrderResponse response = orderService.confirmPayment(authentication, confirmRequest(new BigDecimal(9000)));

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), any());
    }

    @Test
    void 다른_사람의_주문번호로는_결제를_승인할_수_없다() {
        Order order = pendingOrder();
        order.setUser(User.builder().id(2L).username("other_user").build());
        when(orderRepository.findForUpdateByTossOrderId("CE-test-order")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmPayment(authentication, confirmRequest(new BigDecimal(9000))))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), any());
    }

    @Test
    void 결제실패_알림이_늦게_와도_이미_승인된_주문은_그대로_둔다() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findForUpdateByTossOrderId("CE-test-order")).thenReturn(Optional.of(order));

        OrderResponse response = orderService.failPayment(authentication, "CE-test-order", "사용자가 결제를 취소했습니다");

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void 내_주문_목록은_결제대기_실패_주문을_빼고_보여준다() {
        Order order = Order.builder().id(1L).user(user).totalAmount(BigDecimal.TEN)
                .recipientName("홍길동").phone("010").address("주소").status(OrderStatus.PAID).build();
        when(orderRepository.findByUserIdAndStatusInOrderByOrderedAtDescIdDesc(
                eq(1L), argThat(statuses -> !statuses.contains(OrderStatus.PENDING_PAYMENT)
                        && !statuses.contains(OrderStatus.PAYMENT_FAILED)), any()))
                .thenReturn(new SliceImpl<>(List.of(order)));

        List<OrderResponse> result = orderService.getMyOrders(authentication, 0, 20).items();

        assertThat(result).hasSize(1);
    }

    @Test
    void 없는_주문을_조회하면_예외를_던진다() {
        when(orderRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOne(authentication, 999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 결제완료_주문을_취소하면_토스에_환불요청하고_재고를_복구한다() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.PAID);
        order.setPaymentKey("pay_key_123");
        listing.setStock(3);
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancel(authentication, 1L, null);

        verify(tossPaymentsClient).cancel("pay_key_123", "고객 요청 주문 취소", null);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(listing.getStock()).isEqualTo(5);
    }

    @Test
    void 환불이_거절되면_주문과_재고는_그대로다() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.PAID);
        order.setPaymentKey("pay_key_123");
        listing.setStock(3);
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));
        when(tossPaymentsClient.cancel(anyString(), anyString(), any()))
                .thenThrow(new PaymentException("NOT_CANCELABLE_PAYMENT", "취소할 수 없는 결제입니다"));

        assertThatThrownBy(() -> orderService.cancel(authentication, 1L, null)).isInstanceOf(PaymentException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(listing.getStock()).isEqualTo(3);
    }

    @Test
    void 결제전_주문을_취소하면_재고는_건드리지_않는다() {
        Order order = pendingOrder();
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancel(authentication, 1L, null);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(listing.getStock()).isEqualTo(5);
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), any());
    }

    // 입금까지 끝난 가상계좌 주문 (현미 과자 2개, 재고는 이미 차감됨)
    private Order depositedVirtualAccountOrder() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.PAID);
        order.setPaymentKey("pay_key_va");
        order.setVirtualAccountBank("우리은행");
        order.setVirtualAccountNumber("X1234567890");
        listing.setStock(3);
        return order;
    }

    private static RefundAccountRequest refundAccount(String bankCode) {
        RefundAccountRequest refund = new RefundAccountRequest();
        refund.setBankCode(bankCode);
        refund.setAccountNumber("110-123-456789");
        refund.setHolderName(" 홍길동 ");
        return refund;
    }

    @Test
    void 입금이_끝난_가상계좌_주문은_환불계좌와_함께_토스에_취소요청한다() {
        Order order = depositedVirtualAccountOrder();
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancel(authentication, 1L, refundAccount("88"));

        verify(tossPaymentsClient).cancel("pay_key_va", "고객 요청 주문 취소",
                new TossPaymentsClient.RefundAccount("88", "110123456789", "홍길동"));
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(listing.getStock()).isEqualTo(5);
    }

    @Test
    void 입금이_끝난_가상계좌_주문을_환불계좌_없이_취소하면_토스에_요청하지_않는다() {
        Order order = depositedVirtualAccountOrder();
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(authentication, 1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("환불 받을 계좌");
        assertThatThrownBy(() -> orderService.cancel(authentication, 1L, refundAccount("99")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는 은행");
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(listing.getStock()).isEqualTo(3);
    }

    @Test
    void 입금_전_가상계좌_주문은_환불계좌_없이_취소된다() {
        Order order = depositedVirtualAccountOrder();
        order.setStatus(OrderStatus.AWAITING_DEPOSIT);
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancel(authentication, 1L, null);

        verify(tossPaymentsClient).cancel("pay_key_va", "고객 요청 주문 취소", null);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void 이미_취소된_주문을_다시_취소하면_예외를_던진다() {
        Order order = Order.builder().id(1L).user(user).totalAmount(BigDecimal.TEN)
                .recipientName("홍길동").phone("010").address("주소")
                .status(OrderStatus.CANCELLED).build();
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(authentication, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
