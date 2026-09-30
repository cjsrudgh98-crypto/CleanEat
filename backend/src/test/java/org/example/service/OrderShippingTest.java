package org.example.service;

import jakarta.persistence.EntityManager;
import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.User;
import org.example.dto.order.OrderResponse;
import org.example.payment.TossPaymentsClient;
import org.example.repository.OrderRepository;
import org.example.mail.OrderNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 관리자 배송 처리: PAID -> PREPARING -> SHIPPING -> DELIVERED */
@ExtendWith(MockitoExtension.class)
class OrderShippingTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private CartService cartService;

    @Mock
    private TossPaymentsClient tossPaymentsClient;

    @Mock
    private EntityManager entityManager;

    @Mock
    private OrderNotifier orderNotifier;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, currentUserService, cartService, tossPaymentsClient,
                entityManager, orderNotifier);
    }

    private Order order(OrderStatus status) {
        Order order = Order.builder().id(1L).user(User.builder().id(1L).username("buyer").build())
                .totalAmount(BigDecimal.valueOf(4500)).recipientName("홍길동").phone("01012345678").address("주소")
                .paymentMethod("카드").status(status).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void 결제완료_주문을_배송준비로_바꾸면_시작_시각이_기록되고_고객은_취소할_수_없다() {
        Order order = order(OrderStatus.PAID);

        OrderResponse response = orderService.advanceStatus(1L, OrderStatus.PREPARING, null, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
        assertThat(response.getPreparingAt()).isNotNull();
        assertThat(response.isCancelable()).isFalse();
        verify(orderNotifier, never()).notifyShipped(any());
    }

    @Test
    void 발송하면_운송장과_조회링크가_생기고_안내메일을_보낸다() {
        Order order = order(OrderStatus.PREPARING);

        OrderResponse response = orderService.advanceStatus(1L, OrderStatus.SHIPPING, "CJ대한통운", " 612345678901 ");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(order.getTrackingNumber()).isEqualTo("612345678901");
        assertThat(order.getShippedAt()).isNotNull();
        assertThat(response.getTrackingUrl()).endsWith("612345678901");
        verify(orderNotifier).notifyShipped(order);
    }

    @Test
    void 운송장_없이_발송할_수_없다() {
        Order order = order(OrderStatus.PREPARING);

        assertThatThrownBy(() -> orderService.advanceStatus(1L, OrderStatus.SHIPPING, "CJ대한통운", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
        verify(orderNotifier, never()).notifyShipped(any());
    }

    @Test
    void 단계를_건너뛸_수_없다() {
        Order order = order(OrderStatus.PAID);

        assertThatThrownBy(() -> orderService.advanceStatus(1L, OrderStatus.SHIPPING, "CJ대한통운", "123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.advanceStatus(1L, OrderStatus.DELIVERED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void 배송중에는_운송장을_고칠_수_있고_메일은_다시_보내지_않는다() {
        Order order = order(OrderStatus.SHIPPING);
        order.setCourier("CJ대한통운");
        order.setTrackingNumber("111");

        orderService.updateTracking(1L, "한진택배", "222");

        assertThat(order.getCourier()).isEqualTo("한진택배");
        assertThat(order.getTrackingNumber()).isEqualTo("222");
        verify(orderNotifier, never()).notifyShipped(any());
    }

    @Test
    void 배송완료된_주문은_운송장을_고칠_수_없다() {
        order(OrderStatus.DELIVERED);

        assertThatThrownBy(() -> orderService.updateTracking(1L, "한진택배", "222"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 발송후_오래된_배송중_주문은_자동으로_배송완료된다() {
        Order stale = Order.builder().id(2L).status(OrderStatus.SHIPPING)
                .shippedAt(LocalDateTime.now().minusDays(8)).build();
        when(orderRepository.findByStatusAndShippedAtBefore(eq(OrderStatus.SHIPPING), any())).thenReturn(List.of(stale));

        int completed = orderService.autoCompleteDeliveries(7);

        assertThat(completed).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(stale.getDeliveredAt()).isNotNull();
    }
}
