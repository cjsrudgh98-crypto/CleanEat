package org.example.dto.auth;

/**
 * @param devCode 메일 서버가 설정되지 않은 개발 모드에서만 채워지는 인증번호 (운영에서는 항상 null)
 */
public record EmailCodeResponse(String message, long expiresInSeconds, String devCode) {
}
