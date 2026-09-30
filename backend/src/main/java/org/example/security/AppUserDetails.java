package org.example.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/** 스프링 시큐리티 사용자 정보 + 토큰 버전 (JwtAuthenticationFilter가 토큰의 버전과 비교한다) */
public class AppUserDetails extends User {

    private final int tokenVersion;

    public AppUserDetails(String username, String password, Collection<? extends GrantedAuthority> authorities,
                          int tokenVersion) {
        super(username, password, authorities);
        this.tokenVersion = tokenVersion;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }
}
