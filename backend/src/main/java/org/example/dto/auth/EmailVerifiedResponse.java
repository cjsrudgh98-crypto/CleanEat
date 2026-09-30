package org.example.dto.auth;

// 가입 요청에 emailVerificationToken으로 함께 보내야 한다 (30분 유효, 1회용)
public record EmailVerifiedResponse(String verificationToken, long expiresInSeconds) {
}
