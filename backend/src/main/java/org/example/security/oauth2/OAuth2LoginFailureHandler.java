package org.example.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.frontend-url:}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        String redirectBase = (frontendUrl == null || frontendUrl.isBlank()) ? "/" : frontendUrl;
        String redirectUrl = UriComponentsBuilder.fromUriString(redirectBase)
                .queryParam("authError", "oauth_failed")
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
