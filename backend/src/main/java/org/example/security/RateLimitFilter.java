package org.example.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.exception.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 같은 IP에서 짧은 시간에 너무 많이 시도하는 것을 막는다 (비밀번호 무작위 대입, 아이디 존재 여부 탐색, OCR 과부하 방지).
 * 서버 한 대 기준의 메모리 카운터라 서버를 여러 대로 늘리면 Redis 같은 공유 저장소로 바꿔야 한다.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * method 요청 중 경로가 prefix로 시작하는 것을 window 동안 limit 번까지 허용.
     * prefix의 "*"는 경로 한 칸에 대응한다 (예: /api/users/{사용자 id}/email/send-code 를 별표 하나로 표현)
     */
    record Rule(String name, String method, String pathPrefix, int limit, Duration window) {

        Rule(String name, String pathPrefix, int limit, Duration window) {
            this(name, "POST", pathPrefix, limit, window);
        }

        boolean matches(String requestMethod, String uri) {
            if (!method.equalsIgnoreCase(requestMethod)) return false;
            if (!pathPrefix.contains("*")) return uri.startsWith(pathPrefix);
            String[] parts = pathPrefix.split("\\*", -1);
            StringBuilder regex = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) regex.append("[^/]+");
                regex.append(java.util.regex.Pattern.quote(parts[i]));
            }
            return java.util.regex.Pattern.compile(regex.toString()).matcher(uri).lookingAt();
        }
    }

    static final List<Rule> RULES = List.of(
            new Rule("login", "/api/auth/login", 10, Duration.ofMinutes(5)),
            new Rule("register", "/api/auth/register", 5, Duration.ofMinutes(10)),
            new Rule("find-username", "/api/auth/find-username", 5, Duration.ofMinutes(10)),
            new Rule("password-reset", "/api/auth/password-reset", 10, Duration.ofMinutes(10)),
            new Rule("email-code", "/api/auth/email/send-code", 5, Duration.ofMinutes(10)),
            // 인증번호 확인 - 인증번호 하나당 5번 제한과 별개로, 한 곳에서 여러 이메일을 번갈아 대입하는 것도 막는다
            new Rule("email-verify", "/api/auth/email/verify", 20, Duration.ofMinutes(10)),
            new Rule("email-change-code", "/api/users/*/email/send-code", 5, Duration.ofMinutes(10)),
            // 이메일 변경 인증번호 확인 (PATCH) - 인증번호 대입 방지
            new Rule("email-change-verify", "PATCH", "/api/users/*/email", 20, Duration.ofMinutes(10)),
            new Rule("ocr", "/api/scan/image", 20, Duration.ofMinutes(5)),
            new Rule("ocr", "/api/scan/receipt-image", 20, Duration.ofMinutes(5)),
            // 외부 상품 DB(Open Food Facts)를 부르는 스캔 - 영수증 QR 하나가 바코드를 최대 30개까지 조회하므로 따로 묶는다
            // (receipt-image는 위 ocr 규칙이 먼저 잡는다)
            new Rule("receipt", "/api/scan/receipt", 20, Duration.ofMinutes(5)),
            new Rule("barcode", "/api/scan/barcode", 60, Duration.ofMinutes(5)));

    // 오래 안 쓰인 기록이 쌓이지 않도록 이 개수를 넘으면 만료된 것을 한 번 정리한다
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RateLimitFilter(ObjectMapper objectMapper, @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (enabled) {
            Rule rule = ruleFor(request.getMethod(), request.getRequestURI());
            if (rule != null) {
                long retryAfterSeconds = tryAcquire(rule, request.getRemoteAddr(), System.currentTimeMillis());
                if (retryAfterSeconds > 0) {
                    reject(request, response, retryAfterSeconds);
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    static Rule ruleFor(String method, String uri) {
        for (Rule rule : RULES) {
            if (rule.matches(method, uri)) return rule;
        }
        return null;
    }

    /** 허용되면 0, 막히면 다시 시도할 수 있을 때까지 남은 초 */
    long tryAcquire(Rule rule, String clientKey, long nowMillis) {
        if (hits.size() > CLEANUP_THRESHOLD) cleanup(nowMillis);
        Deque<Long> timestamps = hits.computeIfAbsent(rule.name() + "|" + clientKey, k -> new ArrayDeque<>());
        long windowMillis = rule.window().toMillis();
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= nowMillis - windowMillis) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= rule.limit()) {
                long oldest = timestamps.peekFirst();
                return Math.max(1, (oldest + windowMillis - nowMillis + 999) / 1000);
            }
            timestamps.addLast(nowMillis);
            return 0;
        }
    }

    private void cleanup(long nowMillis) {
        long maxWindow = RULES.stream().mapToLong(r -> r.window().toMillis()).max().orElse(0);
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || last <= nowMillis - maxWindow;
            }
        });
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds) throws IOException {
        long minutes = Math.max(1, (retryAfterSeconds + 59) / 60);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json;charset=UTF-8");
        ErrorResponse body = new ErrorResponse(LocalDateTime.now(), HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "요청이 너무 많습니다. " + minutes + "분 후 다시 시도해주세요", request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
