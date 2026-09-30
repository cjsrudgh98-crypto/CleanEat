package org.example.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사용자가 마이페이지에 적은 알레르기와 상품 알레르기 성분을 비교한다.
 * 사용자는 "견과류", "유제품", "달걀"처럼 넓은 말이나 다른 표현을 쓰고,
 * 상품 데이터는 식약처 표시 기준 이름("아몬드", "우유", "계란")을 쓰기 때문에 그 차이를 여기서 맞춘다.
 */
public final class AllergenMatcher {

    // 넓은 분류 -> 그 분류에 속하는 상품 알레르기 이름들
    private static final Map<String, Set<String>> GROUPS = Map.of(
            "견과류", Set.of("견과류", "호두", "잣", "아몬드", "캐슈너트", "피스타치오", "헤이즐넛", "마카다미아", "피칸"),
            "갑각류", Set.of("갑각류", "새우", "게", "랍스터", "가재"),
            "조개류", Set.of("조개류", "굴", "전복", "홍합", "바지락", "가리비"),
            "생선", Set.of("생선", "고등어", "연어", "참치", "멸치")
    );

    // 같은 뜻의 다른 표현 -> 표준 이름
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("유제품", "우유"),
            Map.entry("유당", "우유"),
            Map.entry("달걀", "계란"),
            Map.entry("알류", "계란"),
            Map.entry("난류", "계란"),
            Map.entry("콩", "대두"),
            Map.entry("글루텐", "밀"),
            Map.entry("밀가루", "밀"),
            Map.entry("소고기", "쇠고기"),
            Map.entry("닭", "닭고기"),
            Map.entry("돼지", "돼지고기"),
            Map.entry("견과", "견과류"),
            Map.entry("아황산", "아황산류")
    );

    private AllergenMatcher() {
    }

    /** 사용자 알레르기 목록을 "걸러내야 할 상품 알레르기 이름" 집합으로 펼친다 */
    public static Set<String> expand(Collection<String> userAllergies) {
        Set<String> result = new HashSet<>();
        if (userAllergies == null) return result;
        for (String raw : userAllergies) {
            if (raw == null || raw.isBlank()) continue;
            String name = raw.replaceAll("\\s+", "");
            name = ALIASES.getOrDefault(name, name);
            result.add(name);
            result.addAll(GROUPS.getOrDefault(name, Set.of()));
        }
        return result;
    }

    /** 상품 알레르기 성분 중 사용자에게 해당하는 것들 (없으면 빈 목록) */
    public static List<String> matches(Collection<String> productAllergens, Set<String> expandedUserAllergens) {
        if (productAllergens == null || expandedUserAllergens.isEmpty()) return List.of();
        return productAllergens.stream()
                .filter(expandedUserAllergens::contains)
                .sorted()
                .toList();
    }
}
