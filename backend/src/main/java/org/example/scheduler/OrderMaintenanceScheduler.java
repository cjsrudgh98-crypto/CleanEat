package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주문 관련 주기 작업.
 *  - 가상계좌 입금 확인: 토스 웹훅이 오면 바로 반영되지만, 로컬 개발 환경(localhost)처럼 토스가 웹훅을 보낼 수 없는
 *    곳에서도 동작하도록 입금 대기 주문을 10분마다 토스에 직접 확인한다.
 *  - 오래된 결제대기/결제실패 주문서 정리 (1시간마다)
 *  - 발송 후 app.orders.auto-deliver-days일(기본 7일)이 지난 배송중 주문을 배송 완료로 (1시간마다)
 */
@Component
@RequiredArgsConstructor
public class OrderMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderMaintenanceScheduler.class);

    private final OrderService orderService;

    @Value("${app.orders.auto-deliver-days:7}")
    private int autoDeliverDays;

    @Scheduled(fixedDelayString = "${app.orders.deposit-check-interval-ms:600000}", initialDelay = 60_000)
    public void checkVirtualAccountDeposits() {
        for (String tossOrderId : orderService.awaitingDepositOrderIds()) {
            try {
                orderService.syncWithToss(tossOrderId);
            } catch (RuntimeException e) {
                // 한 주문 실패가 다른 주문 확인을 막지 않게 - 다음 주기에 다시 시도된다
                log.warn("가상계좌 입금 확인 실패: {} ({})", tossOrderId, e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.orders.cleanup-interval-ms:3600000}", initialDelay = 120_000)
    public void deleteStaleOrders() {
        int deleted = orderService.deleteStaleOrders();
        if (deleted > 0) log.info("오래된 결제대기/결제실패 주문서 {}건 정리", deleted);
    }

    @Scheduled(fixedDelayString = "${app.orders.cleanup-interval-ms:3600000}", initialDelay = 180_000)
    public void autoCompleteDeliveries() {
        int completed = orderService.autoCompleteDeliveries(autoDeliverDays);
        if (completed > 0) log.info("발송 후 {}일이 지난 배송중 주문 {}건을 배송 완료로 변경", autoDeliverDays, completed);
    }
}
