package org.example.dto.stats;

import java.time.LocalDate;
import java.util.List;

/**
 * 나의 식습관 통계 (검사 기록 기반). 선택한 기간(최근 N일)과 바로 앞 같은 길이 기간을 비교한다.
 *
 * @param bucketUnit      trend 한 칸의 단위 - DAY(7·30일) / WEEK(90일)
 * @param hasAllergySettings 마이페이지에 알레르기를 설정했는지 (없으면 myAllergens는 항상 비어 있음)
 */
public record EatingStatsResponse(
        int days,
        LocalDate from,
        LocalDate to,
        String bucketUnit,
        Summary current,
        Summary previous,
        List<Bucket> trend,
        List<IngredientCount> topHarmful,
        List<NameCount> myAllergens,
        boolean hasAllergySettings) {

    /**
     * @param withHarmful    유해성분이 하나라도 나온 검사 수
     * @param withMyAllergen 내 알레르기 성분이 들어 있던 검사 수
     */
    public record Summary(int scans, int low, int medium, int high, int withHarmful, int withMyAllergen) {
    }

    /** trend 한 칸 - start부터 하루(DAY) 또는 7일(WEEK) */
    public record Bucket(LocalDate start, int low, int medium, int high) {
    }

    /** @param riskLevel 유해성분 사전의 위험도 (사전에서 지워진 성분이면 null) */
    public record IngredientCount(String name, int count, String riskLevel, String description) {
    }

    public record NameCount(String name, int count) {
    }
}
