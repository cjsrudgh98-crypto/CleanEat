package org.example.service;

import jakarta.persistence.EntityManager;
import org.example.domain.Order;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.domain.Product;
import org.example.domain.StoreListing;
import org.example.domain.User;
import org.example.dto.order.OrderItemResponse;
import org.example.dto.order.OrderResponse;
import org.example.dto.order.RefundAccountRequest;
import org.example.exception.PaymentException;
import org.example.exception.ResourceNotFoundException;
import org.example.mail.OrderNotifier;
import org.example.payment.TossPaymentsClient;
import org.example.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 부분 취소: 상품 한 줄만 환불 + 그 상품 재고만 복구, 마지막 한 줄이면 주문 전체 취소 */
@ExtendWith(MockitoExtension.class)
class OrderPartialCancelTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private CartService cartService;
    @Mock private TossPaymentsClient tossPaymentsClient;
    @Mock private OrderNotifier orderNotifier;
    @Mock private EntityManager entityManager;
    @Mock private Authentication authentication;

    private OrderService orderService;
    private User user;
    private StoreListing snack;   // 4,500원 x 2 = 9,000원 (item 1)
    private StoreListing nuts;    // 3,000원 x 1 = 3,000원 (item 2)

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, currentUserService, cartService, tossPaymentsClient, entityManager,
                orderNotifier);
        user = User.builder().id(1L).username("cleaneat_user").nickname("길동").build();
        snack = StoreListing.builder().id(100L).product(Product.builder().id(10L).name("현미 과자").build())
                .price(new BigDecimal(4500)).stock(3).build();
        nuts = StoreListing.builder().id(200L).product(Product.builder().id(20L).name("구운 아몬드").build())
                .price(new BigDecimal(3000)).stock(7).build();
        lenient().when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
    }

    // 결제 완료된 두 줄짜리 카드 주문 (총 12,000원)
    private Order paidOrder() {
        Order order = Order.builder().id(1L).user(user).totalAmount(new BigDecimal(12000))
                .recipientName("홍길동").phone("010").address("주소").paymentMethod("카드").paymentKey("pay_key_123")
                .tossOrderId("CE-partial").status(OrderStatus.PAID).build();
        OrderItem snackItem = OrderItem.builder().id(1L).order(order).storeListing(snack).productName("현미 과자")
                .unitPrice(new BigDecimal(4500)).quantity(2).build();
        OrderItem nutsItem = OrderItem.builder().id(2L).order(order).storeListing(nuts).productName("구운 아몬드")
                .unitPrice(new BigDecimal(3000)).quantity(1).build();
        order.getItems().add(snackItem);
        order.getItems().add(nutsItem);
        return order;
    }

    private void found(Order order) {
        when(orderRepository.findForUpdateById(1L)).thenReturn(Optional.of(order));
    }

    @Test
    void 상품_한_줄만_그_금액을_환불하고_그_상품_재고만_복구한다() {
        Order order = paidOrder();
        found(order);

        OrderResponse response = orderService.cancelItem(authentication, 1L, 2L, null);

        verify(tossPaymentsClient).cancelPartial(eq("pay_key_123"), contains("구운 아몬드"), eq(new BigDecimal(3000)),
                eq(null), eq("item-2"));
        assertThat(nuts.getStock()).isEqualTo(8);
        assertThat(snack.getStock()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(response.getCancelledAmount()).isEqualByComparingTo("3000");
        assertThat(response.getNetAmount()).isEqualByComparingTo("9000");
        assertThat(response.getItems()).extracting(OrderItemResponse::getId, OrderItemResponse::isCancelled)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, false), org.assertj.core.groups.Tuple.tuple(2L, true));
        // 한 줄만 남았으니 더는 부분 취소가 아니라 주문 취소
        assertThat(response.isItemCancelable()).isFalse();
    }

    @Test
    void 두_줄_이상이면_고객이_부분_취소할_수_있다고_알려준다() {
        Order order = paidOrder();
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

        assertThat(orderService.getOne(authentication, 1L).isItemCancelable()).isTrue();
        order.setStatus(OrderStatus.PREPARING);
        assertThat(orderService.getOne(authentication, 1L).isItemCancelable()).isFalse();
    }

    @Test
    void 마지막_남은_상품을_취소하면_주문_전체_취소가_되고_이미_취소한_상품_재고는_다시_늘리지_않는다() {
        Order order = paidOrder();
        found(order);
        orderService.cancelItem(authentication, 1L, 2L, null);   // 아몬드 먼저 (재고 7 -> 8)

        OrderResponse response = orderService.cancelItem(authentication, 1L, 1L, null);

        // 남은 금액 전부 환불 (금액 없는 전체 취소 = 토스가 남은 금액을 취소)
        verify(tossPaymentsClient).cancel(eq("pay_key_123"), anyString(), eq(null));
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(snack.getStock()).isEqualTo(5);
        assertThat(nuts.getStock()).isEqualTo(8);
    }

    @Test
    void 부분_취소_뒤_반품_승인도_남은_상품_재고만_복구한다() {
        Order order = paidOrder();
        order.getItems().get(1).setCancelledAt(LocalDateTime.now());
        order.setCancelledAmount(new BigDecimal(3000));
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        order.setReturnReason("포장 불량");
        found(order);

        orderService.adminApproveReturn(1L);

        assertThat(snack.getStock()).isEqualTo(5);
        assertThat(nuts.getStock()).isEqualTo(7);
    }

    @Test
    void 이미_취소한_상품이나_없는_상품은_거절한다() {
        Order order = paidOrder();
        order.getItems().get(1).setCancelledAt(LocalDateTime.now());
        found(order);

        assertThatThrownBy(() -> orderService.cancelItem(authentication, 1L, 2L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("이미 취소된 상품");
        assertThatThrownBy(() -> orderService.cancelItem(authentication, 1L, 99L, null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(tossPaymentsClient, never()).cancelPartial(anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void 다른_사람의_주문은_찾을_수_없다() {
        Order order = paidOrder();
        order.setUser(User.builder().id(2L).username("other").build());
        found(order);

        assertThatThrownBy(() -> orderService.cancelItem(authentication, 1L, 2L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 배송_준비가_시작되면_고객은_못하고_관리자는_할_수_있다() {
        Order order = paidOrder();
        order.setStatus(OrderStatus.PREPARING);
        found(order);

        assertThatThrownBy(() -> orderService.cancelItem(authentication, 1L, 2L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("배송 준비");

        orderService.adminCancelItem(1L, 2L, "  아몬드 품절  ", null);
        verify(tossPaymentsClient).cancelPartial(eq("pay_key_123"), eq("아몬드 품절: 구운 아몬드"), eq(new BigDecimal(3000)),
                eq(null), eq("item-2"));
    }

    @Test
    void 가상계좌_결제는_환불_계좌가_있어야_부분_취소된다() {
        Order order = paidOrder();
        order.setPaymentMethod("가상계좌");
        order.setVirtualAccountNumber("X123");
        found(order);

        assertThatThrownBy(() -> orderService.cancelItem(authentication, 1L, 2L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("환불 받을 계좌");

        RefundAccountRequest refund = new RefundAccountRequest();
        refund.setBankCode("88");
        refund.setAccountNumber("110-123-456789");
        refund.setHolderName("홍길동");
        orderService.cancelItem(authentication, 1L, 2L, refund);

        verify(tossPaymentsClient).cancelPartial(eq("pay_key_123"), anyString(), eq(new BigDecimal(3000)),
                eq(new TossPaymentsClient.RefundAccount("88", "110123456789", "홍길동")), eq("item-2"));
    }

    @Test
    void 토스가_거절하면_재고와_금액이_그대로다() {
        Order order = paidOrder();
        found(order);
        when(tossPaymentsClient.cancelPartial(anyString(), anyString(), any(), any(), anyString()))
                .thenThrow(new PaymentException("NOT_CANCELABLE_AMOUNT", "취소할 수 없는 금액입니다"));

        assertThatThrownBy(() -> orderService.cancelItem(authentication, 1L, 2L, null)).isInstanceOf(PaymentException.class);
        assertThat(nuts.getStock()).isEqualTo(7);
        assertThat(order.getItems().get(1).isCancelled()).isFalse();
        assertThat(order.cancelledAmountOrZero()).isEqualByComparingTo("0");
    }
}
