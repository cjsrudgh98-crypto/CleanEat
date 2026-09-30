package org.example.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 영수증에서 읽은 상품명과 DB 상품명을 비교해서 가장 비슷한 상품을 찾는다.
 * 영수증 상품명은 공백 없이 잘려 찍히고("무염구운아몬"), OCR이 글자를 틀리게 읽기도 해서
 * 완전 일치 대신 "비슷한 정도(0~1)"로 판단한다.
 *  - 한쪽이 다른 쪽을 포함하면: 0.5 + 0.5 × (짧은 쪽 길이 / 긴 쪽 길이)  (잘린 이름 대응)
 *  - 아니면: 두 글자씩 묶은 조각(bigram)이 얼마나 겹치는지 (Dice 계수, 오타 대응)
 */
public final class ProductNameMatcher {

    /** 이 점수 이상이어야 같은 상품으로 본다 */
    public static final double THRESHOLD = 0.55;

    public record Match<T>(T item, double score) {
    }

    private ProductNameMatcher() {
    }

    public static <T> Optional<Match<T>> best(String receiptName, Collection<T> candidates, Function<T, String> nameOf) {
        String query = normalize(receiptName);
        if (query.length() < 2) return Optional.empty();

        Match<T> best = null;
        for (T candidate : candidates) {
            double score = similarity(query, normalize(nameOf.apply(candidate)));
            if (score >= THRESHOLD && (best == null || score > best.score())) {
                best = new Match<>(candidate, score);
            }
        }
        return Optional.ofNullable(best);
    }

    // 공백·기호·괄호 속 용량 표기 등을 없애고 한글/영문/숫자만 남긴다: "하루 견과 믹스 (20봉)" -> "하루견과믹스"
    static String normalize(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("\\([^)]*\\)", "")
                .replaceAll("[^0-9a-z가-힣]", "");
    }

    static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 1;
        String shorter = a.length() <= b.length() ? a : b;
        String longer = a.length() <= b.length() ? b : a;
        if (shorter.length() >= 2 && longer.contains(shorter)) {
            // 포함되면 0.5~1 - 길이가 비슷할수록 높게 ("두부"는 "두부채소비빔밥"보다 "유기농두부"에 더 가깝다)
            return 0.5 + 0.5 * shorter.length() / longer.length();
        }
        return dice(a, b);
    }

    private static double dice(String a, String b) {
        if (a.length() < 2 || b.length() < 2) return 0;
        Map<String, Integer> bigrams = new HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) {
            bigrams.merge(a.substring(i, i + 2), 1, Integer::sum);
        }
        int overlap = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String bg = b.substring(i, i + 2);
            Integer count = bigrams.get(bg);
            if (count != null && count > 0) {
                overlap++;
                bigrams.put(bg, count - 1);
            }
        }
        return 2.0 * overlap / ((a.length() - 1) + (b.length() - 1));
    }
}
