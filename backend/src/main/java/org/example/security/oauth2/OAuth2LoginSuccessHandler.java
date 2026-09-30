package org.example.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.dto.auth.AuthResponse;
import org.example.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final OAuthLoginCodeStore codeStore;

    @Value("${app.frontend-url:}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) authentication.getPrincipal();
        AuthResponse authResponse = authService.oauthLogin(
                principal.getProvider(), principal.getProviderId(), principal.getProfileNickname());

        // 토큰을 주소창(URL)에 그대로 넣으면 브라우저 방문 기록/서버 로그에 남는다.
        // 대신 2분짜리 1회용 코드만 넘기고, 프론트가 POST /api/auth/oauth/exchange 로 토큰과 바꾼다.
        String redirectBase = (frontendUrl == null || frontendUrl.isBlank()) ? "/" : frontendUrl;
        String redirectUrl = UriComponentsBuilder.fromUriString(redirectBase)
                .queryParam("oauthCode", codeStore.issue(authResponse))
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
