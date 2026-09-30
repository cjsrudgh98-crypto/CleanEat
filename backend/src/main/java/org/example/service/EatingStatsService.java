package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.Ingredient;
import org.example.domain.ScanHistory;
import org.example.dto.stats.EatingStatsResponse;
import org.example.dto.stats.EatingStatsResponse.Bucket;
import org.example.dto.stats.EatingStatsResponse.IngredientCount;
import org.example.dto.stats.EatingStatsResponse.NameCount;
import org.example.dto.stats.EatingStatsResponse.Summary;
import org.example.repository.IngredientRepository;
import org.example.repository.ScanHistoryRepository;
import org.example.service.UserPreferenceService.Preferences;
import org.example.util.AllergenMatcher;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 나의 식습관 통계. 검사 기록(ScanHistory)만으로 계산한다.
 *  - 기간 요약: 검사 수, 위험도별 수, 유해성분/내 알레르기가 나온 검사 수 (+ 바로 앞 같은 길이 기간과 비교)
 *  - 추이: 하루(7·30일) 또는 1주(90일) 단위 위험도별 검사 수
 *  - 자주 나온 유해성분 / 내 알레르기 성분 순위
 * "내 알레르기"는 지금 마이페이지에 설정된 알레르기 기준이다 (검사 기록에는 상품의 알레르기 성분 전체가 저장돼 있음).
 */
@Service
@RequiredArgsConstructor
public class EatingStatsService {

    public static final Set<Integer> ALLOWED_DAYS = Set.of(7, 30, 90);
    private static final int TOP_LIMIT = 5;

    private final ScanHistoryRepository scanHistoryRepository;
    private final IngredientRepository ingredientRepository;
    private final UserPreferenceService userPreferenceService;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public EatingStatsResponse stats(Long userId, int days, Authentication authentication) {
        currentUserService.assertOwnership(authentication, userId);
        if (!ALLOWED_DAYS.contains(days)) {
            throw new IllegalArgumentException("기간은 7일, 30일, 90일 중에서 고를 수 있습니다");
        }
        Preferences prefs = userPreferenceService.of(authentication);

        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(days - 1L);
        LocalDate previousFrom = from.minusDays(days);

        List<ScanHistory> records = scanHistoryRepository
                .findByUserIdAndScannedAtGreaterThanEqualOrderByScannedAtAsc(userId, previousFrom.atStartOfDay());
        LocalDateTime currentStart = from.atStartOfDay();
        List<ScanHistory> current = records.stream().filter(h -> !h.getScannedAt().isBefore(currentStart)).toList();
        List<ScanHistory> previous = records.stream().filter(h -> h.getScannedAt().isBefore(currentStart)).toList();

        boolean weekly = days > 30;
        return new EatingStatsResponse(
                days, from, today, weekly ? "WEEK" : "DAY",
                summarize(current, prefs),
                summarize(previous, prefs),
                trend(current, from, today, weekly ? 7 : 1),
                topHarmful(current),
                topMyAllergens(current, prefs),
                !prefs.expandedAllergens().isEmpty());
    }

    private static Summary summarize(List<ScanHistory> records, Preferences prefs) {
        int low = 0, medium = 0, high = 0, withHarmful = 0, withMyAllergen = 0;
        for (ScanHistory h : records) {
            switch (h.getOverallRisk()) {
                case LOW -> low++;
                case MEDIUM -> medium++;
                case HIGH -> high++;
            }
            if (!split(h.getMatchedHarmfulIngredients()).isEmpty()) withHarmful++;
            if (!myAllergensIn(h, prefs).isEmpty()) withMyAllergen++;
        }
        return new Summary(records.size(), low, medium, high, withHarmful, withMyAllergen);
    }

    private static List<Bucket> trend(List<ScanHistory> records, LocalDate from, LocalDate to, int bucketDays) {
        int bucketCount = (int) (ChronoUnit.DAYS.between(from, to) / bucketDays) + 1;
        int[][] counts = new int[bucketCount][3];
        for (ScanHistory h : records) {
            int index = (int) (ChronoUnit.DAYS.between(from, h.getScannedAt().toLocalDate()) / bucketDays);
            if (index < 0 || index >= bucketCount) continue;
            counts[index][h.getOverallRisk().ordinal()]++;
        }
        return java.util.stream.IntStream.range(0, bucketCount)
                .mapToObj(i -> new Bucket(from.plusDays((long) i * bucketDays), counts[i][0], counts[i][1], counts[i][2]))
                .toList();
    }

    private List<IngredientCount> topHarmful(List<ScanHistory> records) {
        Map<String, Integer> counts = new HashMap<>();
        for (ScanHistory h : records) {
            // 한 검사에서 같은 성분이 두 번 나와도 1번으로 센다 ("이 성분이 든 제품 수")
            for (String name : new LinkedHashSet<>(split(h.getMatchedHarmfulIngredients()))) {
                counts.merge(name, 1, Integer::sum);
            }
        }
        return ranked(counts).stream()
                .map(e -> {
                    Ingredient ingredient = ingredientRepository.findByNameIgnoreCase(e.name()).orElse(null);
                    return new IngredientCount(e.name(), e.count(),
                            ingredient != null ? ingredient.getRiskLevel().name() : null,
                            ingredient != null ? ingredient.getDescription() : null);
                })
                .toList();
    }

    private static List<NameCount> topMyAllergens(List<ScanHistory> records, Preferences prefs) {
        Map<String, Integer> counts = new HashMap<>();
        for (ScanHistory h : records) {
            for (String name : myAllergensIn(h, prefs)) {
                counts.merge(name, 1, Integer::sum);
            }
        }
        return ranked(counts);
    }

    private static List<String> myAllergensIn(ScanHistory h, Preferences prefs) {
        return AllergenMatcher.matches(new LinkedHashSet<>(split(h.getMatchedAllergens())), prefs.expandedAllergens());
    }

    // 많이 나온 순, 같으면 이름 순으로 상위 TOP_LIMIT개
    private static List<NameCount> ranked(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .map(e -> new NameCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(NameCount::count).reversed().thenComparing(NameCount::name))
                .limit(TOP_LIMIT)
                .toList();
    }

    // 검사 기록에는 목록이 ", "로 이어 붙여 저장돼 있다 (ScanHistoryService.recordForUsername)
    private static List<String> split(String joined) {
        if (joined == null || joined.isBlank()) return List.of();
        return Arrays.stream(joined.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.endsWith("…"))
                .toList();
    }
}
