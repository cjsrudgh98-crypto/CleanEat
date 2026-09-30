package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.domain.EmailVerification.Purpose;
import org.example.dto.auth.AuthResponse;
import org.example.dto.auth.EmailCodeRequest;
import org.example.dto.auth.EmailCodeResponse;
import org.example.dto.auth.EmailVerifiedResponse;
import org.example.dto.auth.FindUsernameRequest;
import org.example.dto.auth.FindUsernameResponse;
import org.example.dto.auth.LoginRequest;
import org.example.dto.auth.OAuthExchangeRequest;
import org.example.dto.auth.PasswordResetCodeRequest;
import org.example.dto.auth.PasswordResetConfirmRequest;
import org.example.dto.auth.PasswordResetVerifyRequest;
import org.example.dto.auth.PasswordResetVerifyResponse;
import org.example.dto.auth.RegisterRequest;
import org.example.security.oauth2.OAuthLoginCodeStore;
import org.example.service.AuthService;
import org.example.service.EmailVerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final OAuthLoginCodeStore oauthLoginCodeStore;

    // ---- 회원가입: 이메일 인증번호 발송 -> 확인 -> 가입 ----

    @PostMapping("/email/send-code")
    public ResponseEntity<EmailCodeResponse> sendRegisterCode(@Valid @RequestBody EmailCodeRequest request) {
        return ResponseEntity.ok(authService.sendRegisterCode(request.getEmail()));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<EmailVerifiedResponse> verifyRegisterCode(@Valid @RequestBody EmailCodeRequest request) {
        String token = emailVerificationService.verify(request.getEmail(), Purpose.REGISTER, request.getCode());
        return ResponseEntity.ok(new EmailVerifiedResponse(token, 30 * 60));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    // ---- 로그인 ----

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // 소셜 로그인 후 주소로 받은 1회용 코드를 토큰으로 교환
    @PostMapping("/oauth/exchange")
    public ResponseEntity<AuthResponse> exchangeOAuthCode(@Valid @RequestBody OAuthExchangeRequest request) {
        return oauthLoginCodeStore.consume(request.getCode())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new IllegalArgumentException("소셜 로그인 정보가 만료되었습니다. 다시 로그인해주세요"));
    }

    // 현재 로그인 정보 (닉네임/권한 최신화) - 토큰이 없거나 만료면 401
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            // 403이 아니라 401 - 프론트는 401을 받으면 저장된 로그인 정보를 지운다
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        return ResponseEntity.ok(authService.me(authentication, token));
    }

    // ---- 아이디 / 비밀번호 찾기 ----

    @PostMapping("/find-username")
    public ResponseEntity<FindUsernameResponse> findUsername(@Valid @RequestBody FindUsernameRequest request) {
        return ResponseEntity.ok(authService.findUsername(request));
    }

    @PostMapping("/password-reset/send-code")
    public ResponseEntity<EmailCodeResponse> sendPasswordResetCode(@Valid @RequestBody PasswordResetCodeRequest request) {
        return ResponseEntity.ok(authService.sendPasswordResetCode(request));
    }

    @PostMapping("/password-reset/verify")
    public ResponseEntity<PasswordResetVerifyResponse> verifyForPasswordReset(
            @Valid @RequestBody PasswordResetVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyForPasswordReset(request));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
