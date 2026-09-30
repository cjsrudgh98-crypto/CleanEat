package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.service.RestockAlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 품절 상품 재입고 알림 신청/취소 (로그인 필요 - SecurityConfig의 anyRequest().authenticated()).
 * /api/products/** 아래에 두지 않는다 - 그 경로는 비로그인 조회를 위해 전부 열려 있다.
 */
@RestController
@RequestMapping("/api/restock-alerts")
@RequiredArgsConstructor
public class RestockAlertController {

    private final RestockAlertService restockAlertService;

    @PutMapping("/{productId}")
    public ResponseEntity<Void> subscribe(@PathVariable Long productId, Authentication authentication) {
        restockAlertService.subscribe(productId, authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> unsubscribe(@PathVariable Long productId, Authentication authentication) {
        restockAlertService.unsubscribe(productId, authentication);
        return ResponseEntity.noContent().build();
    }
}
