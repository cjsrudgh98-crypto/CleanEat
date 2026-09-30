package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.example.dto.order.OrderResponse;
import org.example.dto.payment.PaymentConfigResponse;
import org.example.dto.payment.PaymentConfirmRequest;
import org.example.dto.payment.PaymentFailRequest;
import org.example.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final OrderService orderService;
    private final String clientKey;

    public PaymentController(OrderService orderService, @Value("${app.toss.client-key}") String clientKey) {
        this.orderService = orderService;
        this.clientKey = clientKey;
    }

    @GetMapping("/config")
    public ResponseEntity<PaymentConfigResponse> config() {
        return ResponseEntity.ok(new PaymentConfigResponse(clientKey));
    }

    @PostMapping("/confirm")
    public ResponseEntity<OrderResponse> confirm(@Valid @RequestBody PaymentConfirmRequest request,
                                                 Authentication authentication) {
        return ResponseEntity.ok(orderService.confirmPayment(authentication, request));
    }

    /**
     * 토스페이먼츠 웹훅 (개발자센터 > 웹훅에 https://<도메인>/api/payments/webhook 로 등록 - DEPLOYMENT.md 참고).
     * 두 가지 모양이 온다:
     *  - 가상계좌 입금 알림(DEPOSIT_CALLBACK): { "orderId", "status", "secret", ... }
     *  - 결제 상태 변경(PAYMENT_STATUS_CHANGED): { "eventType", "data": { 결제 객체 (orderId, secret 포함) } }
     * 요청 내용은 위조될 수 있으므로 주문번호/secret만 꺼내고, 실제 상태는 토스 API로 다시 조회해서 반영한다.
     * 토스는 200이 아니면 재전송하므로 처리할 게 없어도 200을 돌려준다 (토스 조회가 실패하면 5xx -> 토스가 재전송).
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody JsonNode body) {
        JsonNode payload = body.has("data") && body.get("data").isObject() ? body.get("data") : body;
        String orderId = textOrNull(payload.path("orderId"));
        if (orderId != null) {
            orderService.handleDepositWebhook(orderId, textOrNull(payload.path("secret")));
        }
        return ResponseEntity.ok().build();
    }

    private static String textOrNull(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    @PostMapping("/fail")
    public ResponseEntity<OrderResponse> fail(@Valid @RequestBody PaymentFailRequest request,
                                              Authentication authentication) {
        String reason = request.getMessage() != null ? request.getMessage() : "결제가 취소되었습니다";
        return ResponseEntity.ok(orderService.failPayment(authentication, request.getOrderId(), reason));
    }
}
