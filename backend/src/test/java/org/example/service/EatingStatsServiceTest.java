package org.example.service;

import org.example.domain.Ingredient;
import org.example.domain.RiskLevel;
import org.example.domain.ScanHistory;
import org.example.dto.stats.EatingStatsResponse;
import org.example.repository.IngredientRepository;
import org.example.repository.ScanHistoryRepository;
import org.example.service.UserPreferenceService.Preferences;
import org.example.util.AllergenMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EatingStatsServiceTest {

    // 오늘 = 2026-09-30 (고정)
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    @Mock
    private ScanHistoryRepository scanHistoryRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private UserPreferenceService userPreferenceService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private Authentication authentication;

    private EatingStatsService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new EatingStatsService(scanHistoryRepository, ingredientRepository, userPreferenceService,
                currentUserService, clock);
        // 알레르기 설정: 견과류 (-> 호두/아몬드/잣 등으로 펼쳐짐)
        lenient().when(userPreferenceService.of(authentication)).thenReturn(
                new Preferences(true, Set.of(), List.of("견과류"), AllergenMatcher.expand(List.of("견과류"))));
        lenient().when(ingredientRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
    }

    private static ScanHistory scan(LocalDateTime at, RiskLevel risk, String harmful, String allergens) {
        return ScanHistory.builder().scannedAt(at).overallRisk(risk)
                .matchedHarmfulIngredients(harmful).matchedAllergens(allergens).build();
    }

    private void records(ScanHistory... scans) {
        when(scanHistoryRepository.findByUserIdAndScannedAtGreaterThanEqualOrderByScannedAtAsc(eq(1L), eq(
                TODAY.minusDays(59).atStartOfDay()))).thenReturn(List.of(scans));
    }

    @Test
    void 기간_요약과_앞_기간을_나눠서_센다() {
        records(
                // 앞 기간 (31~60일 전)
                scan(TODAY.minusDays(40).atTime(9, 0), RiskLevel.HIGH, "아스파탐", ""),
                // 이번 기간 (최근 30일)
                scan(TODAY.minusDays(29).atTime(0, 0), RiskLevel.LOW, "", "우유"),
                scan(TODAY.minusDays(3).atTime(10, 0), RiskLevel.MEDIUM, "아스파탐, 타르색소", "호두, 대두"),
                scan(TODAY.atTime(8, 0), RiskLevel.HIGH, "아스파탐", "아몬드"));

        EatingStatsResponse stats = service.stats(1L, 30, authentication);

        assertThat(stats.from()).isEqualTo(TODAY.minusDays(29));
        assertThat(stats.to()).isEqualTo(TODAY);
        assertThat(stats.current()).isEqualTo(new EatingStatsResponse.Summary(3, 1, 1, 1, 2, 2));
        assertThat(stats.previous()).isEqualTo(new EatingStatsResponse.Summary(1, 0, 0, 1, 1, 0));
        assertThat(stats.hasAllergySettings()).isTrue();
    }

    @Test
    void 유해성분과_내_알레르기를_많이_나온_순으로_센다() {
        when(ingredientRepository.findByNameIgnoreCase("아스파탐")).thenReturn(Optional.of(
                Ingredient.builder().name("아스파탐").riskLevel(RiskLevel.MEDIUM).description("인공감미료").build()));
        records(
                scan(TODAY.minusDays(3).atTime(10, 0), RiskLevel.MEDIUM, "아스파탐, 타르색소, 아스파탐", "호두, 우유"),
                scan(TODAY.minusDays(1).atTime(10, 0), RiskLevel.MEDIUM, "아스파탐", "호두"),
                // 잘려서 저장된 마지막 항목("…")은 세지 않는다
                scan(TODAY.atTime(8, 0), RiskLevel.HIGH, "타르색…", "아몬드"));

        EatingStatsResponse stats = service.stats(1L, 30, authentication);

        assertThat(stats.topHarmful()).extracting(EatingStatsResponse.IngredientCount::name).containsExactly("아스파탐", "타르색소");
        assertThat(stats.topHarmful().get(0).count()).isEqualTo(2);
        assertThat(stats.topHarmful().get(0).riskLevel()).isEqualTo("MEDIUM");
        // 우유는 내 알레르기가 아니라 빠진다
        assertThat(stats.myAllergens()).containsExactly(
                new EatingStatsResponse.NameCount("호두", 2), new EatingStatsResponse.NameCount("아몬드", 1));
    }

    @Test
    void 칠일은_하루_단위_구십일은_일주일_단위로_추이를_만든다() {
        when(scanHistoryRepository.findByUserIdAndScannedAtGreaterThanEqualOrderByScannedAtAsc(eq(1L), eq(
                TODAY.minusDays(13).atStartOfDay()))).thenReturn(List.of(
                scan(TODAY.minusDays(6).atTime(1, 0), RiskLevel.LOW, "", ""),
                scan(TODAY.atTime(23, 59), RiskLevel.HIGH, "", ""),
                scan(TODAY.atTime(9, 0), RiskLevel.HIGH, "", "")));

        EatingStatsResponse week = service.stats(1L, 7, authentication);

        assertThat(week.bucketUnit()).isEqualTo("DAY");
        assertThat(week.trend()).hasSize(7);
        assertThat(week.trend().get(0)).isEqualTo(new EatingStatsResponse.Bucket(TODAY.minusDays(6), 1, 0, 0));
        assertThat(week.trend().get(6)).isEqualTo(new EatingStatsResponse.Bucket(TODAY, 0, 0, 2));

        when(scanHistoryRepository.findByUserIdAndScannedAtGreaterThanEqualOrderByScannedAtAsc(eq(1L), eq(
                TODAY.minusDays(179).atStartOfDay()))).thenReturn(List.of(
                scan(TODAY.minusDays(89).atTime(1, 0), RiskLevel.MEDIUM, "", ""),
                scan(TODAY.atTime(1, 0), RiskLevel.LOW, "", "")));

        EatingStatsResponse quarter = service.stats(1L, 90, authentication);

        assertThat(quarter.bucketUnit()).isEqualTo("WEEK");
        assertThat(quarter.trend()).hasSize(13);
        assertThat(quarter.trend().get(0).medium()).isEqualTo(1);
        assertThat(quarter.trend().get(12).low()).isEqualTo(1);
    }

    @Test
    void 알레르기를_설정하지_않았으면_내_알레르기는_비어_있다() {
        when(userPreferenceService.of(authentication)).thenReturn(Preferences.NONE);
        records(scan(TODAY.atTime(8, 0), RiskLevel.LOW, "", "호두"));

        EatingStatsResponse stats = service.stats(1L, 30, authentication);

        assertThat(stats.hasAllergySettings()).isFalse();
        assertThat(stats.myAllergens()).isEmpty();
        assertThat(stats.current().withMyAllergen()).isZero();
    }

    @Test
    void 정해진_기간만_고를_수_있다() {
        assertThatThrownBy(() -> service.stats(1L, 45, authentication)).isInstanceOf(IllegalArgumentException.class);
    }
}
