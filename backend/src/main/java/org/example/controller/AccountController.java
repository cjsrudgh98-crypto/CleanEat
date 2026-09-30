package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.account.ChangeEmailRequest;
import org.example.dto.account.ChangePasswordRequest;
import org.example.dto.auth.EmailCodeRequest;
import org.example.dto.auth.EmailCodeResponse;
import org.example.dto.account.UpdateNicknameRequest;
import org.example.security.JwtAuthenticationFilter;
import org.example.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{id}")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PatchMapping("/nickname")
    public ResponseEntity<Void> updateNickname(@PathVariable("id") Long userId,
                                                @Valid @RequestBody UpdateNicknameRequest request,
                                                Authentication authentication) {
        accountService.updateNickname(userId, request, authentication);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@PathVariable("id") Long userId,
                                                @Valid @RequestBody ChangePasswordRequest request,
                                                Authentication authentication) {
        String newToken = accountService.changePassword(userId, request, authentication);
        // 이전 토큰은 무효가 되므로 새 토큰을 내려준다 - 프론트는 이 헤더를 받으면 저장된 토큰을 바꿔 끼운다
        return ResponseEntity.noContent().header(JwtAuthenticationFilter.REFRESHED_TOKEN_HEADER, newToken).build();
    }

    // 이메일 변경(소셜 가입자는 등록): 새 이메일로 인증번호 발송 -> 인증번호와 함께 변경 요청
    @PostMapping("/email/send-code")
    public ResponseEntity<EmailCodeResponse> sendEmailChangeCode(@PathVariable("id") Long userId,
                                                                 @Valid @RequestBody EmailCodeRequest request,
                                                                 Authentication authentication) {
        return ResponseEntity.ok(accountService.sendEmailChangeCode(userId, request.getEmail(), authentication));
    }

    @PatchMapping("/email")
    public ResponseEntity<Void> changeEmail(@PathVariable("id") Long userId,
                                            @Valid @RequestBody ChangeEmailRequest request,
                                            Authentication authentication) {
        accountService.changeEmail(userId, request, authentication);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@PathVariable("id") Long userId, Authentication authentication) {
        accountService.deleteAccount(userId, authentication);
        return ResponseEntity.noContent().build();
    }
}
