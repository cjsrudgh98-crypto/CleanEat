package org.example.security;

import jakarta.servlet.FilterChain;
import org.example.security.JwtTokenProvider.TokenClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static AppUserDetails user(int tokenVersion) {
        return new AppUserDetails("cleaneat_user", "x", List.of(new SimpleGrantedAuthority("ROLE_USER")), tokenVersion);
    }

    private static TokenClaims claims(int tokenVersion) {
        return new TokenClaims("cleaneat_user", tokenVersion, new Date(System.currentTimeMillis() + 3_600_000));
    }

    private static MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void 유효한_토큰이면_인증정보를_SecurityContext에_설정한다() throws Exception {
        MockHttpServletRequest request = requestWithToken("valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.parse("valid-token")).thenReturn(Optional.of(claims(0)));
        when(userDetailsService.loadUserByUsername("cleaneat_user")).thenReturn(user(0));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("cleaneat_user");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void 비밀번호_변경_전에_발급된_토큰이면_인증하지_않는다() throws Exception {
        MockHttpServletRequest request = requestWithToken("old-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.parse("old-token")).thenReturn(Optional.of(claims(0)));
        when(userDetailsService.loadUserByUsername("cleaneat_user")).thenReturn(user(1));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // 무효 토큰을 새 토큰으로 바꿔 주지도 않는다
        assertThat(response.getHeader(JwtAuthenticationFilter.REFRESHED_TOKEN_HEADER)).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void 만료가_가까우면_같은_버전으로_새_토큰을_내려준다() throws Exception {
        MockHttpServletRequest request = requestWithToken("valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        TokenClaims tokenClaims = claims(2);
        when(jwtTokenProvider.parse("valid-token")).thenReturn(Optional.of(tokenClaims));
        when(userDetailsService.loadUserByUsername("cleaneat_user")).thenReturn(user(2));
        when(jwtTokenProvider.shouldRefresh(tokenClaims)).thenReturn(true);
        when(jwtTokenProvider.generateToken("cleaneat_user", 2)).thenReturn("refreshed-token");

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader(JwtAuthenticationFilter.REFRESHED_TOKEN_HEADER)).isEqualTo("refreshed-token");
    }

    @Test
    void 토큰이_없으면_인증정보를_설정하지_않고_체인만_진행한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verify(userDetailsService, never()).loadUserByUsername(any());
    }

    @Test
    void 유효하지_않은_토큰이면_인증정보를_설정하지_않는다() throws Exception {
        MockHttpServletRequest request = requestWithToken("invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenProvider.parse("invalid-token")).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void Bearer_접두사가_없으면_토큰을_무시한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "raw-token-without-prefix");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).parse(any());
        verify(filterChain).doFilter(request, response);
    }
}
