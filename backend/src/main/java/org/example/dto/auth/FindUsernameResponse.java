package org.example.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class FindUsernameResponse {
    // 전체 아이디를 그대로 노출하지 않고 일부를 * 로 가린다
    private String maskedUsername;
    private LocalDate joinedAt;
}
