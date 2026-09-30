package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.common.PageResponse;
import org.example.dto.order.CancelOrderRequest;
import org.example.dto.order.CreateOrderRequest;
import org.example.dto.order.OrderResponse;
import org.example.dto.order.ReturnRequest;
import org.example.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request,
                                                 Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createFromCart(authentication, request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<OrderResponse>> getMyOrders(@RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size,
                                                                   Authentication authentication) {
        return ResponseEntity.ok(orderService.getMyOrders(authentication, page, size));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOne(@PathVariable Long orderId, Authentication authentication) {
        return ResponseEntity.ok(orderService.getOne(authentication, orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable Long orderId,
                                                 @Valid @RequestBody(required = false) CancelOrderRequest request,
                                                 Authentication authentication) {
        return ResponseEntity.ok(orderService.cancel(authentication, orderId,
                request != null ? request.getRefundAccount() : null));
    }

    // 부분 취소 - 상품 한 줄만 (본문은 입금 끝난 가상계좌 주문일 때만 - 환불 계좌)
    @PostMapping("/{orderId}/items/{itemId}/cancel")
    public ResponseEntity<OrderResponse> cancelItem(@PathVariable Long orderId, @PathVariable Long itemId,
                                                    @Valid @RequestBody(required = false) CancelOrderRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.ok(orderService.cancelItem(authentication, orderId, itemId,
                request != null ? request.getRefundAccount() : null));
    }

    // 반품 신청 (배송 완료 후 기간 안) / 신청 철회 (관리자 처리 전까지)
    @PostMapping("/{orderId}/return")
    public ResponseEntity<OrderResponse> requestReturn(@PathVariable Long orderId,
                                                       @Valid @RequestBody ReturnRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(orderService.requestReturn(authentication, orderId, request.getReason(),
                request.getRefundAccount()));
    }

    @PostMapping("/{orderId}/return/withdraw")
    public ResponseEntity<OrderResponse> withdrawReturn(@PathVariable Long orderId, Authentication authentication) {
        return ResponseEntity.ok(orderService.withdrawReturn(authentication, orderId));
    }
}
