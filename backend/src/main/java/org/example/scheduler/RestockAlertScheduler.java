package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.service.RestockAlertService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 재입고 알림 주기 확인. 관리자가 재고를 채우면 그 자리에서 보내지만, 주문 취소/반품/입금 기한 만료로
 * 재고가 돌아온 경우는 여기서 잡는다 (기본 5분마다).
 */
@Component
@RequiredArgsConstructor
public class RestockAlertScheduler {

    private final RestockAlertService restockAlertService;

    @Scheduled(fixedDelayString = "${app.restock.check-interval-ms:300000}", initialDelay = 90_000)
    public void sendRestockAlerts() {
        restockAlertService.sendRestocked();
    }
}
