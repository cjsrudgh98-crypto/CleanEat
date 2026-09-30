package org.example.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secret",
                "test-jwt-signing-secret-for-unit-tests-should-be-long-enough");
        ReflectionTestUtils.setField(jwtTokenProvider, "expirationMs", 3600000L);
        jwtTokenProvider.init();
    }

    @Test
    void 토큰을_생성하고_같은_사용자명을_추출한다() {
        String token = jwtTokenProvider.generateToken("cleaneat_user", 0);

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.getUsername(token)).isEqualTo("cleaneat_user");
    }

    @Test
    void 정상_토큰은_유효하다() {
        String token = jwtTokenProvider.generateToken("cleaneat_user", 0);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void 만료된_토큰은_유효하지_않다() {
        ReflectionTestUtils.setField(jwtTokenProvider, "expirationMs", -1000L);
        String expiredToken = jwtTokenProvider.generateToken("cleaneat_user", 0);

        assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    void 서명이_다른_토큰은_유효하지_않다() {
        JwtTokenProvider otherProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(otherProvider, "secret",
                "a-completely-different-jwt-signing-secret-for-this-test-case");
        ReflectionTestUtils.setField(otherProvider, "expirationMs", 3600000L);
        otherProvider.init();
        String tokenFromOtherSecret = otherProvider.generateToken("cleaneat_user", 0);

        assertThat(jwtTokenProvider.validateToken(tokenFromOtherSecret)).isFalse();
    }

    @Test
    void 토큰에_담은_버전을_꺼낼_수_있다() {
        String token = jwtTokenProvider.generateToken("cleaneat_user", 3);

        assertThat(jwtTokenProvider.parse(token)).get()
                .extracting(JwtTokenProvider.TokenClaims::tokenVersion).isEqualTo(3);
    }

    @Test
    void 버전이_없는_예전_토큰은_버전_0으로_읽는다() {
        // 버전 claim 추가 전에 발급된 토큰 - 배포 직후 기존 로그인이 끊기지 않아야 한다
        javax.crypto.SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                "test-jwt-signing-secret-for-unit-tests-should-be-long-enough".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String legacyToken = io.jsonwebtoken.Jwts.builder().subject("cleaneat_user")
                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();

        assertThat(jwtTokenProvider.parse(legacyToken)).get()
                .extracting(JwtTokenProvider.TokenClaims::tokenVersion).isEqualTo(0);
    }

    @Test
    void 형식이_깨진_토큰은_유효하지_않다() {
        assertThat(jwtTokenProvider.validateToken("not-a-jwt-token")).isFalse();
    }
}
