package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.domain.OrderStatus;
import org.example.dto.admin.AdminCancelRequest;
import org.example.dto.admin.AdminOrderStatusRequest;
import org.example.dto.admin.AdminProductRequest;
import org.example.dto.admin.AdminProductResponse;
import org.example.dto.admin.AdminReturnRejectRequest;
import org.example.dto.admin.AdminSummaryResponse;
import org.example.dto.admin.AdminTrackingRequest;
import org.example.dto.admin.SalesReportResponse;
import org.example.dto.admin.StockAdjustRequest;
import org.example.dto.common.PageResponse;
import org.example.dto.order.OrderResponse;
import org.example.service.AdminProductService;
import org.example.service.AdminSummaryService;
import org.example.service.OrderService;
import org.example.service.SalesReportService;
import org.example.shipping.Couriers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 관리자 전용 API. 권한 검사는 SecurityConfig에서 /api/admin/** 전체에 ROLE_ADMIN을 요구한다.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminSummaryService adminSummaryService;
    private final SalesReportService salesReportService;
    private final AdminProductService adminProductService;
    private final OrderService orderService;

    @GetMapping("/summary")
    public ResponseEntity<AdminSummaryResponse> summary() {
        return ResponseEntity.ok(adminSummaryService.summary());
    }

    // 기간별 매출 (period: 7d / 30d = 일별, 12m = 월별)
    @GetMapping("/sales")
    public ResponseEntity<SalesReportResponse> sales(@RequestParam(defaultValue = "30d") String period) {
        return ResponseEntity.ok(salesReportService.report(period));
    }

    // ---- 상품 / 재고 ----

    @GetMapping("/products")
    public ResponseEntity<List<AdminProductResponse>> products() {
        return ResponseEntity.ok(adminProductService.list());
    }

    @PostMapping("/products")
    public ResponseEntity<AdminProductResponse> createProduct(@Valid @RequestBody AdminProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminProductService.create(request));
    }

    @PutMapping("/products/{productId}")
    public ResponseEntity<AdminProductResponse> updateProduct(@PathVariable Long productId,
                                                              @Valid @RequestBody AdminProductRequest request) {
        return ResponseEntity.ok(adminProductService.update(productId, request));
    }

    @PostMapping("/products/{productId}/stock")
    public ResponseEntity<AdminProductResponse> adjustStock(@PathVariable Long productId,
                                                            @Valid @RequestBody StockAdjustRequest request) {
        return ResponseEntity.ok(adminProductService.adjustStock(productId, request.getDelta()));
    }

    // ---- 주문 ----

    // status를 비우면 결제대기/결제실패를 뺀 전체 (예: ?status=PAID&status=PREPARING)
    @GetMapping("/orders")
    public ResponseEntity<PageResponse<OrderResponse>> orders(@RequestParam(required = false) Set<OrderStatus> status,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.adminList(status, page, size));
    }

    @PostMapping("/orders/{orderId}/status")
    public ResponseEntity<OrderResponse> advanceStatus(@PathVariable Long orderId,
                                                       @Valid @RequestBody AdminOrderStatusRequest request) {
        return ResponseEntity.ok(orderService.advanceStatus(
                orderId, request.getStatus(), request.getCourier(), request.getTrackingNumber()));
    }

    // 가상계좌 입금 대기 주문을 지금 토스에 확인 (웹훅을 기다리지 않고)
    @PostMapping("/orders/{orderId}/sync-payment")
    public ResponseEntity<OrderResponse> syncPayment(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.adminSyncPayment(orderId));
    }

    // 배송중 주문의 운송장 정보 수정 (잘못 입력했을 때)
    @PutMapping("/orders/{orderId}/tracking")
    public ResponseEntity<OrderResponse> updateTracking(@PathVariable Long orderId,
                                                        @Valid @RequestBody AdminTrackingRequest request) {
        return ResponseEntity.ok(orderService.updateTracking(orderId, request.getCourier(), request.getTrackingNumber()));
    }

    // 발송 처리 화면의 택배사 선택지 (배송 조회 링크를 만들 수 있는 택배사)
    @GetMapping("/couriers")
    public ResponseEntity<List<String>> couriers() {
        return ResponseEntity.ok(Couriers.names());
    }

    // 부분 취소 - 상품 한 줄만 (배송 준비중까지)
    @PostMapping("/orders/{orderId}/items/{itemId}/cancel")
    public ResponseEntity<OrderResponse> cancelItem(@PathVariable Long orderId, @PathVariable Long itemId,
                                                    @Valid @RequestBody(required = false) AdminCancelRequest request) {
        return ResponseEntity.ok(orderService.adminCancelItem(orderId, itemId,
                request != null ? request.getReason() : null,
                request != null ? request.getRefundAccount() : null));
    }

    // 반품 승인 (토스 전액 환불 + 재고 복구) / 거절 (사유는 고객에게 보임)
    @PostMapping("/orders/{orderId}/return/approve")
    public ResponseEntity<OrderResponse> approveReturn(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.adminApproveReturn(orderId));
    }

    @PostMapping("/orders/{orderId}/return/reject")
    public ResponseEntity<OrderResponse> rejectReturn(@PathVariable Long orderId,
                                                      @Valid @RequestBody AdminReturnRejectRequest request) {
        return ResponseEntity.ok(orderService.adminRejectReturn(orderId, request.getReason()));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable Long orderId,
                                                @Valid @RequestBody(required = false) AdminCancelRequest request) {
        return ResponseEntity.ok(orderService.adminCancel(orderId,
                request != null ? request.getReason() : null,
                request != null ? request.getRefundAccount() : null));
    }
}
