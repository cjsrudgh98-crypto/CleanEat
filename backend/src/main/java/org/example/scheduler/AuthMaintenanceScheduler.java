package org.example.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.service.EmailVerificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 이메일 인증번호 기록 정리. 인증번호는 5분, 인증 완료 토큰은 30분이면 만료되므로
 * 하루 지난 기록은 쓸 일이 없다 - 쌓이지 않게 1시간마다 지운다.
 */
@Component
@RequiredArgsConstructor
public class AuthMaintenanceScheduler {

    private final EmailVerificationService emailVerificationService;

    @Scheduled(fixedDelayString = "${app.auth.verification-cleanup-interval-ms:3600000}", initialDelay = 240_000)
    public void deleteOldEmailVerifications() {
        emailVerificationService.deleteOlderThan(LocalDateTime.now().minusDays(1));
    }
}
