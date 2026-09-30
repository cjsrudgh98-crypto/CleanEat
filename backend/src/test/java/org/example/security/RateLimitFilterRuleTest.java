package org.example.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterRuleTest {

    @Test
    void 별표는_경로_한_칸에_대응한다() {
        assertThat(RateLimitFilter.ruleFor("POST", "/api/users/15/email/send-code").name()).isEqualTo("email-change-code");
        assertThat(RateLimitFilter.ruleFor("POST", "/api/users/15/email")).isNull();
        assertThat(RateLimitFilter.ruleFor("POST", "/api/users/15/x/email/send-code")).isNull();
    }

    @Test
    void 인증번호_확인도_제한한다() {
        assertThat(RateLimitFilter.ruleFor("POST", "/api/auth/email/verify").name()).isEqualTo("email-verify");
        assertThat(RateLimitFilter.ruleFor("POST", "/api/auth/login").name()).isEqualTo("login");
    }

    @Test
    void 외부_상품DB를_부르는_스캔도_제한한다() {
        assertThat(RateLimitFilter.ruleFor("POST", "/api/scan/receipt").name()).isEqualTo("receipt");
        assertThat(RateLimitFilter.ruleFor("POST", "/api/scan/barcode").name()).isEqualTo("barcode");
        // 영수증 사진은 OCR 규칙으로 (receipt 규칙과 겹치지 않게)
        assertThat(RateLimitFilter.ruleFor("POST", "/api/scan/receipt-image").name()).isEqualTo("ocr");
    }

    @Test
    void 이메일_변경_인증번호_확인은_PATCH로_제한한다() {
        assertThat(RateLimitFilter.ruleFor("PATCH", "/api/users/15/email").name()).isEqualTo("email-change-verify");
        // 같은 경로라도 규칙에 없는 메서드는 제한하지 않는다
        assertThat(RateLimitFilter.ruleFor("GET", "/api/auth/login")).isNull();
        assertThat(RateLimitFilter.ruleFor("PATCH", "/api/users/15/nickname")).isNull();
    }
}
