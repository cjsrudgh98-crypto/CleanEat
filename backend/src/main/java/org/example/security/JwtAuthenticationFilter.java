package org.example.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.security.JwtTokenProvider.TokenClaims;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Authorization";
    private static final String PREFIX = "Bearer ";
    // 토큰 유효시간이 절반 이하로 남으면 새 토큰을 이 헤더로 내려준다 - 프론트가 받아서 바꿔 끼운다 (활동 중이면 로그인 유지)
    public static final String REFRESHED_TOKEN_HEADER = "X-Auth-Token";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER_NAME);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            jwtTokenProvider.parse(token).ifPresent(claims -> authenticate(claims, request, response));
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(TokenClaims claims, HttpServletRequest request, HttpServletResponse response) {
        String username = claims.username();
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            // 탈퇴 등으로 없어진 사용자의 토큰 - 비로그인으로 취급한다.
            // (예외를 그대로 두면 누구나 볼 수 있는 상품 목록 같은 API까지 실패함)
            return;
        }
        // 비밀번호 변경/재설정 전에 발급된 토큰 - 비로그인으로 취급한다 (도둑맞은 토큰도 비밀번호를 바꾸면 끊긴다)
        if (userDetails instanceof AppUserDetails app && app.getTokenVersion() != claims.tokenVersion()) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        if (jwtTokenProvider.shouldRefresh(claims)) {
            response.setHeader(REFRESHED_TOKEN_HEADER, jwtTokenProvider.generateToken(username, claims.tokenVersion()));
        }
    }
}
