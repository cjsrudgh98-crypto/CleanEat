package org.example.security;

import lombok.RequiredArgsConstructor;
import org.example.security.oauth2.CustomOAuth2UserService;
import org.example.security.oauth2.OAuth2LoginFailureHandler;
import org.example.security.oauth2.OAuth2LoginSuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JsonAccessDeniedHandler jsonAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // OAuth2 관련 빈들은 생성자 필드가 아니라 이 메서드의 파라미터로만 받는다.
    // 필드로 받으면 SecurityConfig 인스턴스 생성 자체가 이 빈들에 묶여서
    // AuthService(passwordEncoder 필요) -> SecurityConfig -> OAuth2LoginSuccessHandler -> AuthService
    // 순환참조가 생긴다.
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oAuth2LoginFailureHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 관리자 API - 토큰의 사용자를 매 요청 DB에서 다시 읽으므로, 권한을 빼면 바로 막힌다
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/auth/**", "/api/scan/**", "/api/products/**", "/api/payments/config", "/api/payments/webhook", "/h2-console/**").permitAll()
                        // 헬스체크 (배포 플랫폼이 로그인 없이 호출) - 노출된 actuator는 health 하나뿐이고 세부 정보는 숨긴다
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // 공개 유해성분 사전 (조회만 - 수정은 /api/admin/ingredients)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/ingredients").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/code/**").permitAll()
                        .requestMatchers("/", "/index.html", "/favicon.svg", "/assets/**", "/static/**", "/images/**").permitAll()
                        // 홈 화면에 추가(PWA) - 매니페스트/서비스 워커/아이콘/오프라인 화면
                        .requestMatchers("/manifest.json", "/sw.js", "/offline.html", "/icons/**").permitAll()
                        .requestMatchers("/cart", "/checkout", "/orders", "/orders/*", "/products", "/products/*", "/mypage",
                                "/payments/success", "/payments/fail", "/admin", "/admin/*", "/stats", "/favorites", "/ingredients").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jsonAccessDeniedHandler))
                // 같은 사이트 안에서만 iframe 허용 (H2 콘솔용) - 다른 사이트가 CleanEat을 몰래 겹쳐 띄우는 클릭재킹 방지
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // "oauth" 프로필이 꺼져있으면 ClientRegistrationRepository 빈이 없음 - 이때는 oauth2Login을 켜지 않는다
        // (켜려고 하면 빈을 못 찾아서 기동이 실패함)
        if (clientRegistrationRepositoryProvider.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureHandler(oAuth2LoginFailureHandler)
            );
        }

        return http.build();
    }
}
