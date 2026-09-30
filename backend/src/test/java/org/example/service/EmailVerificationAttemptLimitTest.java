package org.example.service;

import org.example.domain.EmailVerification.Purpose;
import org.example.domain.User;
import org.example.dto.auth.PasswordResetCodeRequest;
import org.example.dto.auth.PasswordResetVerifyRequest;
import org.example.repository.EmailVerificationRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 인증번호 5회 제한이 실제 DB에서 지켜지는지.
 * 비밀번호 재설정은 AuthService의 트랜잭션 안에서 verify를 부르고, 틀리면 그 트랜잭션이 롤백된다.
 * 틀린 횟수가 같은 트랜잭션에 있으면 함께 롤백돼서 무제한 대입이 가능했다 - 그래서 목(mock) 없이 확인한다.
 */
@SpringBootTest
class EmailVerificationAttemptLimitTest {

    private static final String USERNAME = "attempt_limit_user";
    private static final String EMAIL = "attempt-limit@example.com";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @BeforeEach
    void setUp() {
        userRepository.save(User.builder()
                .username(USERNAME).nickname("시도제한").password("x").name("홍길동").email(EMAIL).build());
    }

    @AfterEach
    void tearDown() {
        emailVerificationRepository.deleteAll(emailVerificationRepository.findAll().stream()
                .filter(v -> v.getEmail().equals(EMAIL)).toList());
        userRepository.findByUsername(USERNAME).ifPresent(userRepository::delete);
    }

    @Test
    void 비밀번호_재설정_인증번호를_5번_틀리면_맞는_번호도_거절한다() {
        PasswordResetCodeRequest sendRequest = new PasswordResetCodeRequest();
        sendRequest.setUsername(USERNAME);
        sendRequest.setEmail(EMAIL);
        String code = authService.sendPasswordResetCode(sendRequest).devCode();
        assertThat(code).as("테스트는 메일 서버 없는 개발 모드에서 인증번호를 받는다").isNotNull();
        String wrongCode = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < EmailVerificationService.MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> authService.verifyForPasswordReset(verifyRequest(wrongCode)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(emailVerificationRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(EMAIL, Purpose.PASSWORD_RESET))
                .get().extracting(v -> v.getFailedAttempts()).isEqualTo(EmailVerificationService.MAX_ATTEMPTS);
        assertThatThrownBy(() -> authService.verifyForPasswordReset(verifyRequest(code)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("만료");
    }

    private static PasswordResetVerifyRequest verifyRequest(String code) {
        PasswordResetVerifyRequest request = new PasswordResetVerifyRequest();
        request.setUsername(USERNAME);
        request.setEmail(EMAIL);
        request.setCode(code);
        return request;
    }
}
