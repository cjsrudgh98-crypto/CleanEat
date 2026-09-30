package org.example.service;

import jakarta.persistence.EntityManager;
import org.example.domain.Order;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.domain.Product;
import org.example.domain.StoreListing;
import org.example.domain.User;
import org.example.dto.order.OrderResponse;
import org.example.dto.order.RefundAccountRequest;
import org.example.exception.PaymentException;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 반품: 고객 신청/철회 -> 관리자 승인(환불+재고 복구)/거절 */
@ExtendWith(MockitoExtension.class)
class OrderReturnTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private CartService cartService;
    @Mock private TossPaymentsClient tossPaymentsClient;
    @Mock private OrderNotifier orderNotifier;
    @Mock private EntityManager entityManager;
    @Mock private Authentication authentication;

    private OrderService orderService;
    private User user;
    private StoreListing listing;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, currentUserService, cartService, tossPaymentsClient, entityManager,
                orderNotifier);
        user = User.builder().id(1L).username("cleaneat_user").nickname("길동").build();
        Product product = Product.builder().id(10L).name("무첨가 현미 과자").build();
        listing = StoreListing.builder().id(100L).product(product).price(new BigDecimal(4500)).stock(3).build();
        lenient().when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
    }

    // days일 전에 배송 완료된 카드 결제 주문 (현미 과자 2개, 재고는 이미 차감됨)
    private Order deliveredOrder(int daysAgo) {
        OrderItem item = OrderItem.builder().id(1L).storeListing(listing).productName("무첨가 현미 과자")
                .unitPrice(new BigDecimal(4500)).quantity(2).build();
        Order order = Order.builder().id(1L).user(user).totalAmount(new BigDecimal(9000))
                .recipientName("홍길동").phone("010").address("주소").paymentMethod("카드").paymentKey("pay_key_123")
                .tossOrderId("CE-return").status(OrderStatus.DELIVERED)
                .shippedAt(LocalDateTime.now().minusDays(daysAgo + 2)).deliveredAt(LocalDateTime.now().minusDays(daysAgo))
                .build();
        item.setOrder(order);
        order.getItems().add(item);
        return order;
    }

    private Order virtualAccountOrder(int daysAgo) {
        Order order = deliveredOrder(daysAgo);
        order.setPaymentMethod("가상계좌");
        order.setVirtualAccountBank("우리은행");
        order.setVirtualAccountNumber("X1234567890");
        return order;
    }

    private static RefundAccountRequest refundAccount() {
        RefundAccountRequest refund = new RefundAccountRequest();
        refund.setBankCode("88");
        refund.setAccountNumber("110-123-456789");
        refund.setHolderName("홍길동");
        return refund;
    }

    // ---------------- 고객 신청 ----------------

    @Test
    void 배송_완료_후_기간_안이면_반품을_신청할_수_있다() {
        Order order = deliveredOrder(2);
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.requestReturn(authentication, 1L, "  포장이 뜯겨 왔어요  ", null);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.RETURN_REQUESTED);
        assertThat(response.getReturnReason()).isEqualTo("포장이 뜯겨 왔어요");
        assertThat(response.getReturnRequestedAt()).isNotNull();
        assertThat(response.isReturnable()).isFalse();
        // 신청만으로는 환불/재고 복구를 하지 않는다 (관리자 승인 때)
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), any());
        assertThat(listing.getStock()).isEqualTo(3);
    }

    @Test
    void 배송_완료된_주문은_반품_가능과_마감일을_알려준다() {
        Order order = deliveredOrder(2);
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOne(authentication, 1L);

        assertThat(response.isReturnable()).isTrue();
        assertThat(response.getReturnDeadline()).isEqualTo(order.getDeliveredAt().plusDays(7));
    }

    @Test
    void 배송_완료_후_7일이_지나면_신청할_수_없다() {
        Order order = deliveredOrder(8);
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

        assertThat(orderService.getOne(authentication, 1L).isReturnable()).isFalse();
        assertThatThrownBy(() -> orderService.requestReturn(authentication, 1L, "단순 변심", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7일이 지나");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void 배송_완료_전에는_반품이_아니라_취소_대상이다() {
        Order order = deliveredOrder(0);
        order.setStatus(OrderStatus.SHIPPING);
        order.setDeliveredAt(null);
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.requestReturn(authentication, 1L, "단순 변심", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("배송이 완료된 주문만");
    }

    @Test
    void 거절된_적_있는_주문은_다시_신청할_수_없다() {
        Order order = deliveredOrder(1);
        order.setReturnRejectedAt(LocalDateTime.now());
        order.setReturnRejectReason("사용 흔적이 있어 반품이 어렵습니다");
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

        assertThat(orderService.getOne(authentication, 1L).isReturnable()).isFalse();
        assertThatThrownBy(() -> orderService.requestReturn(authentication, 1L, "다시 요청", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("거절된 주문");
    }

    @Test
    void 가상계좌_결제는_환불_계좌가_있어야_신청되고_계좌는_하이픈_없이_저장한다() {
        Order order = virtualAccountOrder(1);
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.requestReturn(authentication, 1L, "단순 변심", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("환불 받을 계좌");

        OrderResponse response = orderService.requestReturn(authentication, 1L, "단순 변심", refundAccount());

        assertThat(order.getReturnRefundBank()).isEqualTo("88");
        assertThat(order.getReturnRefundAccount()).isEqualTo("110123456789");
        assertThat(order.getReturnRefundHolder()).isEqualTo("홍길동");
        // 관리자 화면에는 끝 4자리만
        assertThat(response.getReturnRefundAccountSummary()).isEqualTo("신한은행 ****6789 홍길동");
    }

    @Test
    void 처리_전이면_신청을_철회할_수_있고_계좌는_지우며_다시_신청할_수_있다() {
        Order order = virtualAccountOrder(1);
        when(orderRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(order));
        orderService.requestReturn(authentication, 1L, "단순 변심", refundAccount());

        OrderResponse response = orderService.withdrawReturn(authentication, 1L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(response.getReturnReason()).isNull();
        assertThat(order.getReturnRefundAccount()).isNull();
        assertThat(response.isReturnable()).isTrue();
    }

    // ---------------- 관리자 승인 / 거절 ----------------

    @Test
    void 승인하면_토스로_전액_환불하고_재고를_복구한다() {
        Order order = deliveredOrder(1);
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        order.setReturnReason("포장 불량");
        when(orderRepository.findForUpdateById(1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.adminApproveReturn(1L);

        verify(tossPaymentsClient).cancel("pay_key_123", "반품: 포장 불량", null);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.RETURNED);
        assertThat(response.getReturnedAt()).isNotNull();
        assertThat(listing.getStock()).isEqualTo(5);
        verify(orderNotifier).notifyReturnApproved(order);
    }

    @Test
    void 가상계좌_반품_승인은_받아둔_계좌로_환불하고_계좌를_지운다() {
        Order order = virtualAccountOrder(1);
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        order.setReturnReason("단순 변심");
        order.setReturnRefundBank("88");
        order.setReturnRefundAccount("110123456789");
        order.setReturnRefundHolder("홍길동");
        when(orderRepository.findForUpdateById(1L)).thenReturn(Optional.of(order));

        orderService.adminApproveReturn(1L);

        verify(tossPaymentsClient).cancel("pay_key_123", "반품: 단순 변심",
                new TossPaymentsClient.RefundAccount("88", "110123456789", "홍길동"));
        assertThat(order.getReturnRefundAccount()).isNull();
        assertThat(order.getReturnRefundBank()).isNull();
    }

    @Test
    void 토스가_환불을_거절하면_반품_신청_상태와_재고가_그대로다() {
        Order order = deliveredOrder(1);
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        order.setReturnReason("포장 불량");
        when(orderRepository.findForUpdateById(1L)).thenReturn(Optional.of(order));
        when(tossPaymentsClient.cancel(anyString(), anyString(), any()))
                .thenThrow(new PaymentException("NOT_CANCELABLE_PAYMENT", "취소할 수 없는 결제입니다"));

        assertThatThrownBy(() -> orderService.adminApproveReturn(1L)).isInstanceOf(PaymentException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURN_REQUESTED);
        assertThat(listing.getStock()).isEqualTo(3);
        verify(orderNotifier, never()).notifyReturnApproved(any());
    }

    @Test
    void 거절하면_배송_완료로_돌아가고_사유가_남으며_계좌는_지운다() {
        Order order = virtualAccountOrder(1);
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        order.setReturnReason("단순 변심");
        order.setReturnRefundAccount("110123456789");
        when(orderRepository.findForUpdateById(1L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.adminRejectReturn(1L, "  개봉 후 사용한 상품은 반품이 어렵습니다 ");

        assertThat(response.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(response.getReturnRejectReason()).isEqualTo("개봉 후 사용한 상품은 반품이 어렵습니다");
        assertThat(response.isReturnable()).isFalse();
        assertThat(order.getReturnRefundAccount()).isNull();
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), any());
        verify(orderNotifier).notifyReturnRejected(order);
    }

    @Test
    void 반품_신청_중이_아닌_주문은_승인도_거절도_안_된다() {
        Order order = deliveredOrder(1);
        when(orderRepository.findForUpdateById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.adminApproveReturn(1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orderService.adminRejectReturn(1L, "사유")).isInstanceOf(IllegalArgumentException.class);
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), any());
    }

    @Test
    void 반품_신청_중은_판매된_주문이고_진행중이며_반품_완료는_아니다() {
        // 리뷰 작성 자격 / 회원 탈퇴 제한에 쓰인다
        assertThat(OrderStatus.RETURN_REQUESTED.isPurchased()).isTrue();
        assertThat(OrderStatus.RETURN_REQUESTED.isInProgress()).isTrue();
        assertThat(OrderStatus.RETURNED.isPurchased()).isFalse();
        assertThat(OrderStatus.RETURNED.isInProgress()).isFalse();
    }
}
