package org.example.security.oauth2;

import org.example.dto.auth.AuthResponse;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 소셜 로그인 성공 후 프론트로 넘기는 1회용 코드 보관소.
 * 코드는 추측할 수 없는 난수이고, 2분 안에 한 번만 토큰으로 바꿀 수 있다.
 * (서버 한 대 기준 메모리 저장 - 여러 대로 늘리면 Redis 등 공유 저장소로 바꿔야 함)
 */
@Component
public class OAuthLoginCodeStore {

    static final Duration TTL = Duration.ofMinutes(2);

    private record Entry(AuthResponse response, long expiresAt) {
    }

    private final Map<String, Entry> codes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String issue(AuthResponse response) {
        long now = System.currentTimeMillis();
        codes.values().removeIf(e -> e.expiresAt() < now);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codes.put(code, new Entry(response, now + TTL.toMillis()));
        return code;
    }

    /** 코드를 꺼내면서 지운다 (한 번만 사용 가능). 없거나 만료됐으면 empty */
    public Optional<AuthResponse> consume(String code) {
        if (code == null) return Optional.empty();
        Entry entry = codes.remove(code);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) return Optional.empty();
        return Optional.of(entry.response());
    }
}
