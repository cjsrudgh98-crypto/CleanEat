package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.EmailVerification;
import org.example.domain.EmailVerification.Purpose;
import org.example.mail.EmailSender;
import org.example.repository.EmailVerificationRepository;
import org.example.util.Hashing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 이메일 인증번호 발송/확인.
 *  - 인증번호 6자리, 5분 유효, 5번 틀리면 무효
 *  - 같은 메일로는 1분에 한 번만 재발송
 *  - 확인에 성공하면 30분짜리 "인증 완료 토큰"을 주고, 가입/비밀번호 재설정 때 이 토큰을 함께 받아 1회 소비한다
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    static final Duration VERIFIED_TTL = Duration.ofMinutes(30);
    static final int MAX_ATTEMPTS = 5;

    private final EmailVerificationRepository repository;
    private final EmailSender emailSender;
    private final PlatformTransactionManager transactionManager;

    /** @param devCode 메일 서버 없이 개발 모드로 동작할 때만 인증번호 (화면에 보여주기용), 실제 발송이면 null */
    public record SendResult(long expiresInSeconds, String devCode) {
    }

    @Transactional
    public SendResult sendCode(String rawEmail, Purpose purpose) {
        String email = normalize(rawEmail);
        repository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .filter(last -> last.getCreatedAt().isAfter(LocalDateTime.now().minus(RESEND_COOLDOWN)))
                .ifPresent(last -> {
                    throw new IllegalArgumentException("인증번호는 1분에 한 번만 보낼 수 있습니다. 잠시 후 다시 시도해주세요");
                });

        String code = Hashing.sixDigitCode();
        repository.deleteByEmailAndPurpose(email, purpose);
        repository.save(EmailVerification.builder()
                .email(email)
                .purpose(purpose)
                .codeHash(Hashing.sha256(email + ":" + code))
                .expiresAt(LocalDateTime.now().plus(CODE_TTL))
                .build());

        String subject = switch (purpose) {
            case REGISTER -> "[CleanEat] 회원가입 인증번호";
            case PASSWORD_RESET -> "[CleanEat] 비밀번호 재설정 인증번호";
            case EMAIL_CHANGE -> "[CleanEat] 이메일 변경 인증번호";
        };
        emailSender.send(email, subject, "인증번호: " + code + "\n\n5분 안에 입력해주세요. 본인이 요청하지 않았다면 이 메일을 무시하세요.");
        return new SendResult(CODE_TTL.toSeconds(), emailSender.isDevMode() ? code : null);
    }

    /** 인증번호 확인 -> 성공하면 인증 완료 토큰 (가입/재설정 요청에 함께 보내야 함) */
    @Transactional
    public String verify(String rawEmail, Purpose purpose, String code) {
        String email = normalize(rawEmail);
        EmailVerification v = repository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new IllegalArgumentException("인증번호를 먼저 요청해주세요"));
        if (v.getExpiresAt().isBefore(LocalDateTime.now()) || v.getFailedAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("인증번호가 만료되었습니다. 다시 요청해주세요");
        }
        if (code == null || !Hashing.sha256(email + ":" + code.trim()).equals(v.getCodeHash())) {
            recordFailedAttempt(v.getId());
            int left = MAX_ATTEMPTS - (v.getFailedAttempts() + 1);
            throw new IllegalArgumentException(left > 0
                    ? "인증번호가 올바르지 않습니다 (남은 시도 " + left + "회)"
                    : "인증번호를 5번 틀렸습니다. 다시 요청해주세요");
        }
        String token = Hashing.randomToken();
        v.setVerifiedTokenHash(Hashing.sha256(token));
        v.setVerifiedTokenExpiresAt(LocalDateTime.now().plus(VERIFIED_TTL));
        return token;
    }

    /**
     * 틀린 횟수는 별도 트랜잭션으로 바로 저장한다. verify를 부른 쪽(비밀번호 재설정/이메일 변경)의 트랜잭션은
     * 이 예외로 롤백되므로, 같은 트랜잭션에 두면 횟수 증가도 함께 취소돼서 5회 제한이 동작하지 않는다.
     */
    private void recordFailedAttempt(Long verificationId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> repository.incrementFailedAttempts(verificationId));
    }

    /** 인증 완료 토큰을 확인하고 소비한다 (한 번만 쓸 수 있음). 유효하지 않으면 예외 */
    @Transactional
    public void consume(String rawEmail, Purpose purpose, String verificationToken) {
        String email = normalize(rawEmail);
        EmailVerification v = verificationToken == null ? null : repository
                .findByEmailAndPurposeAndVerifiedTokenHash(email, purpose, Hashing.sha256(verificationToken))
                .orElse(null);
        if (v == null || v.getVerifiedTokenExpiresAt() == null || v.getVerifiedTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("이메일 인증이 만료되었거나 완료되지 않았습니다. 인증을 다시 진행해주세요");
        }
        repository.delete(v);
    }

    @Transactional
    public void deleteOlderThan(LocalDateTime before) {
        repository.deleteByCreatedAtBefore(before);
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
