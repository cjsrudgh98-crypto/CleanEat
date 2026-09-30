package org.example.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

// 비밀번호 재설정 2단계: 아이디 + 이메일 + 메일로 받은 인증번호
@Getter
@Setter
public class PasswordResetVerifyRequest {

    @NotBlank(message = "아이디는 필수입니다")
    private String username;

    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "이메일 형식이 올바르지 않습니다")
    private String email;

    @NotBlank(message = "인증번호를 입력해주세요")
    @Pattern(regexp = "^\\d{6}$", message = "인증번호는 숫자 6자리입니다")
    private String code;
}
