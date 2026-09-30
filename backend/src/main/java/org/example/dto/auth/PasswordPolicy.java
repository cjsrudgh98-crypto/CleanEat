package org.example.dto.auth;

/**
 * 회원가입 / 비밀번호 재설정 / 비밀번호 변경에서 똑같은 규칙을 쓰도록 한 곳에 모아둔다.
 */
public final class PasswordPolicy {

    // 8~64자, 영문 + 숫자 + 특수문자를 각각 1개 이상 포함
    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S{8,64}$";
    public static final String MESSAGE = "비밀번호는 영문, 숫자, 특수문자를 모두 포함한 8~64자여야 합니다";

    private PasswordPolicy() {
    }
}
