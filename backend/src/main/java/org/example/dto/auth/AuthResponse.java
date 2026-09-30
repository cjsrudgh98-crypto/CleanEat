package org.example.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long userId;
    private String username;
    private String nickname;
    // USER / ADMIN - 프론트가 관리자 메뉴 노출 여부를 판단한다 (실제 권한 검사는 서버가 매 요청마다 함)
    private String role;
}
