package org.example.controller;

import org.example.domain.Order;
import org.example.domain.OrderItem;
import org.example.domain.OrderStatus;
import org.example.domain.StoreListing;
import org.example.domain.User;
import org.example.exception.PaymentException;
import org.example.payment.TossPaymentsClient;
import org.example.payment.TossPaymentsClient.TossPayment;
import org.example.repository.OrderRepository;
import org.example.repository.StoreListingRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 토스 가상계좌 입금 웹훅 (/api/payments/webhook) - 토스 API만 가짜로 두고 실제 DB로 확인한다 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentWebhookTest {

    private static final String SECRET = "va-secret-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreListingRepository storeListingRepository;

    @MockBean
    private TossPaymentsClient tossPaymentsClient;

    private StoreListing listing;
    private int stockBefore;

    @BeforeEach
    void setUp() {
        listing = storeListingRepository.findAll().get(0);
        stockBefore = listing.getStock();
    }

    /** 가상계좌 발급 후 입금 대기 주문 (결제 승인 때 재고 2개를 이미 확보한 상태) */
    private Order awaitingOrder(String tossOrderId, LocalDateTime dueDate) {
        User buyer = userRepository.save(User.builder().username("buyer_" + System.nanoTime()).nickname("구매자")
                .password("x").email("buyer@example.com").build());
        Order order = Order.builder().user(buyer).status(OrderStatus.AWAITING_DEPOSIT)
                .totalAmount(BigDecimal.valueOf(9000)).recipientName("홍길동").phone("01012345678").address("주소")
                .paymentMethod("가상계좌").orderName("테스트 주문").tossOrderId(tossOrderId).paymentKey("pay_" + tossOrderId)
                .virtualAccountBank("우리은행").virtualAccountNumber("1234567890").virtualAccountDueDate(dueDate)
                .virtualAccountSecret(SECRET).build();
        order.getItems().add(OrderItem.builder().order(order).storeListing(listing)
                .productName("상품").unitPrice(BigDecimal.valueOf(4500)).quantity(2).build());
        return orderRepository.save(order);
    }

    private static TossPayment toss(String orderId, String status, long amount) {
        return new TossPayment("pay_" + orderId, orderId, status, "가상계좌", BigDecimal.valueOf(amount),
                "2026-09-30T10:00:00+09:00", null);
    }

    private void depositCallback(String orderId, String secret) throws Exception {
        mockMvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"createdAt\":\"2026-09-30T10:00:00\",\"secret\":\"" + secret
                                + "\",\"status\":\"DONE\",\"transactionKey\":\"tx\",\"orderId\":\"" + orderId + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 입금_알림이_오면_토스에_다시_확인하고_결제완료로_바꾼다() throws Exception {
        Order order = awaitingOrder("CE-deposit-1", LocalDateTime.now().plusDays(1));
        when(tossPaymentsClient.getByOrderId("CE-deposit-1")).thenReturn(toss("CE-deposit-1", "DONE", 9000));

        depositCallback("CE-deposit-1", SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(listing.getStock()).isEqualTo(stockBefore);
    }

    @Test
    void 결제_상태_변경_이벤트_모양도_처리한다() throws Exception {
        Order order = awaitingOrder("CE-deposit-2", LocalDateTime.now().plusDays(1));
        when(tossPaymentsClient.getByOrderId("CE-deposit-2")).thenReturn(toss("CE-deposit-2", "DONE", 9000));

        mockMvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"createdAt\":\"2026-09-30T10:00:00\","
                                + "\"data\":{\"orderId\":\"CE-deposit-2\",\"status\":\"DONE\",\"secret\":\"" + SECRET + "\"}}"))
                .andExpect(status().isOk());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void secret이_다르면_위조로_보고_토스에_묻지도_않는다() throws Exception {
        Order order = awaitingOrder("CE-deposit-3", LocalDateTime.now().plusDays(1));

        depositCallback("CE-deposit-3", "forged-secret");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_DEPOSIT);
        verify(tossPaymentsClient, never()).getByOrderId(anyString());
    }

    @Test
    void 웹훅_내용이_완료여도_토스_조회가_입금전이면_그대로_둔다() throws Exception {
        Order order = awaitingOrder("CE-deposit-4", LocalDateTime.now().plusDays(1));
        when(tossPaymentsClient.getByOrderId("CE-deposit-4")).thenReturn(toss("CE-deposit-4", "WAITING_FOR_DEPOSIT", 9000));

        depositCallback("CE-deposit-4", SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_DEPOSIT);
    }

    @Test
    void 입금_금액이_다르면_자동으로_결제완료하지_않는다() throws Exception {
        Order order = awaitingOrder("CE-deposit-5", LocalDateTime.now().plusDays(1));
        when(tossPaymentsClient.getByOrderId("CE-deposit-5")).thenReturn(toss("CE-deposit-5", "DONE", 100));

        depositCallback("CE-deposit-5", SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_DEPOSIT);
    }

    @Test
    void 입금기한이_지나_취소되면_재고를_한_번만_되돌린다() throws Exception {
        Order order = awaitingOrder("CE-deposit-6", LocalDateTime.now().minusHours(1));
        when(tossPaymentsClient.getByOrderId("CE-deposit-6")).thenReturn(toss("CE-deposit-6", "CANCELED", 9000));

        depositCallback("CE-deposit-6", SECRET);
        // 토스 재전송 - 이미 취소된 주문이라 아무것도 하지 않아야 한다
        depositCallback("CE-deposit-6", SECRET);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getFailReason()).contains("입금 기한");
        assertThat(listing.getStock()).isEqualTo(stockBefore + 2);
    }

    @Test
    void 모르는_주문번호나_주문번호_없는_요청도_200() throws Exception {
        depositCallback("CE-unknown", SECRET);
        mockMvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content("{\"eventType\":\"X\"}"))
                .andExpect(status().isOk());
        verify(tossPaymentsClient, never()).getByOrderId(anyString());
    }

    @Test
    void 토스_조회가_실패하면_200이_아니라서_토스가_다시_보낸다() throws Exception {
        Order order = awaitingOrder("CE-deposit-7", LocalDateTime.now().plusDays(1));
        when(tossPaymentsClient.getByOrderId("CE-deposit-7")).thenThrow(new PaymentException("TEMP", "일시적 오류"));

        mockMvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"" + SECRET + "\",\"status\":\"DONE\",\"orderId\":\"CE-deposit-7\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(200));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_DEPOSIT);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 관리자는_입금확인_버튼으로_바로_확인한다() throws Exception {
        Order order = awaitingOrder("CE-deposit-8", LocalDateTime.now().plusDays(1));
        when(tossPaymentsClient.getByOrderId("CE-deposit-8")).thenReturn(toss("CE-deposit-8", "DONE", 9000));

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/sync-payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        // 이미 결제완료된 주문에는 400
        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/sync-payment"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 가상계좌_secret은_고객_응답에_나오지_않는다() {
        assertThat(List.of(org.example.dto.order.OrderResponse.class.getDeclaredFields()))
                .noneMatch(f -> f.getName().toLowerCase().contains("secret"));
    }
}
