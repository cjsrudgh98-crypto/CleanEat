package org.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// "오늘" 기준 계산(식습관 통계 기간 등)을 테스트에서 고정된 날짜로 돌릴 수 있게 시계를 빈으로 둔다
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
