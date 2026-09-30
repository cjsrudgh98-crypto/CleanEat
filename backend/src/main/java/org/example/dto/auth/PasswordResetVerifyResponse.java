package org.example.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PasswordResetVerifyResponse {
    private String resetToken;
    private long expiresInSeconds;
}
