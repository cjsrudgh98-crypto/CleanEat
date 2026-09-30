package org.example.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OAuthExchangeRequest {
    @NotBlank(message = "로그인 코드가 없습니다")
    private String code;
}
