package org.example.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtTokenProvider {

    // 사용자의 tokenVersion - 비밀번호를 바꾸면 DB 값이 올라가서 이전 토큰과 달라진다
    static final String VERSION_CLAIM = "ver";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey key;

    /** 서명이 확인된 토큰 내용 */
    public record TokenClaims(String username, int tokenVersion, Date expiration) {
    }

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, int tokenVersion) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username)
                .claim(VERSION_CLAIM, tokenVersion)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /** 서명/만료를 확인하고 내용을 꺼낸다. 유효하지 않으면 empty */
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            // 버전 claim이 생기기 전에 발급된 토큰은 0으로 본다 (배포 직후 기존 로그인이 끊기지 않게)
            Integer version = claims.get(VERSION_CLAIM, Integer.class);
            return Optional.of(new TokenClaims(claims.getSubject(), version != null ? version : 0, claims.getExpiration()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String getUsername(String token) {
        return parse(token).map(TokenClaims::username)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다"));
    }

    /** 남은 유효시간이 전체의 절반 이하인지 (이때 필터가 새 토큰을 발급해 준다) */
    public boolean shouldRefresh(TokenClaims claims) {
        long remaining = claims.expiration().getTime() - System.currentTimeMillis();
        return remaining < expirationMs / 2;
    }

    public boolean validateToken(String token) {
        return parse(token).isPresent();
    }
}
