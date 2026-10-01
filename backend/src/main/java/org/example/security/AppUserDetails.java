package org.example.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * 스프링 시큐리티 사용자 정보 + 회원 id + 토큰 버전 (JwtAuthenticationFilter가 토큰의 버전과 비교한다).
 * 필터가 요청마다 DB에서 새로 읽어 만들므로, 서비스는 회원 id를 다시 조회하지 않고 여기서 꺼내 쓸 수 있다.
 */
public class AppUserDetails extends User {

    private final Long userId;
    private final int tokenVersion;

    public AppUserDetails(Long userId, String username, String password,
                          Collection<? extends GrantedAuthority> authorities, int tokenVersion) {
        super(username, password, authorities);
        this.userId = userId;
        this.tokenVersion = tokenVersion;
    }

    public Long getUserId() {
        return userId;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    /** 로그인 정보에 담긴 회원 id. AppUserDetails가 아닌 인증(테스트용 목 사용자 등)이면 null - 호출한 쪽이 DB에서 찾는다 */
    public static Long userIdOf(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AppUserDetails app ? app.getUserId() : null;
    }
}
