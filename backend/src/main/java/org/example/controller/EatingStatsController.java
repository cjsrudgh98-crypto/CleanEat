package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.stats.EatingStatsResponse;
import org.example.service.EatingStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{id}/stats")
@RequiredArgsConstructor
public class EatingStatsController {

    private final EatingStatsService eatingStatsService;

    // 나의 식습관 통계 (days = 7 / 30 / 90)
    @GetMapping
    public ResponseEntity<EatingStatsResponse> stats(@PathVariable("id") Long userId,
                                                     @RequestParam(defaultValue = "30") int days,
                                                     Authentication authentication) {
        return ResponseEntity.ok(eatingStatsService.stats(userId, days, authentication));
    }
}
