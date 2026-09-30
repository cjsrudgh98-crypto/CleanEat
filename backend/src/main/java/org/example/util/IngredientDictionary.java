package org.example.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 성분표 글자에서 알레르기 유발물질을 찾아 "한국어 표준 이름"으로 돌려준다.
 * Open Food Facts의 해외 제품 성분표는 영어·프랑스어라 한국어 이름만으로 비교하면 거의 잡히지 않는다 -
 * 그래서 여러 표기를 여기서 한국어 이름으로 모아준다.
 * (유해성분은 관리자가 늘릴 수 있도록 DB 사전 + HarmfulIngredientMatcher로 찾는다 - seed/harmful-ingredients.json)
 */
public final class IngredientDictionary {

    /**
     * 알레르기 표준 이름(AllergenMatcher / 상품 데이터와 같은 이름) -> 여러 언어 표기.
     * 한 글자 한국어(밀, 게, 잣)는 다른 단어 속에 우연히 들어갈 수 있어서 SHORT_KOREAN 규칙으로 따로 다룬다.
     */
    private static final Map<String, List<String>> ALLERGENS = Map.ofEntries(
            Map.entry("우유", List.of("우유", "밀크", "유청", "탈지분유", "전지분유", "유당", "치즈", "버터", "크림", "카제인",
                    "milk", "whey", "lactose", "butter", "cream", "cheese", "casein",
                    "lait", "lactosérum", "beurre", "crème", "fromage", "babeurre")),
            Map.entry("계란", List.of("계란", "달걀", "난백", "난황", "전란", "egg", "oeuf", "œuf", "oeufs", "œufs")),
            Map.entry("밀", List.of("밀가루", "소맥", "wheat", "blé", "froment", "gluten")),
            Map.entry("대두", List.of("대두", "콩기름", "두유", "soy", "soja", "soya")),
            Map.entry("땅콩", List.of("땅콩", "peanut", "arachide", "cacahuète")),
            Map.entry("호두", List.of("호두", "walnut", "noix de grenoble")),
            Map.entry("아몬드", List.of("아몬드", "almond", "amande")),
            Map.entry("헤이즐넛", List.of("헤이즐넛", "hazelnut", "noisette")),
            Map.entry("캐슈너트", List.of("캐슈", "cashew", "noix de cajou")),
            Map.entry("피스타치오", List.of("피스타치오", "pistachio", "pistache")),
            Map.entry("견과류", List.of("견과", "tree nut", "fruits à coque")),
            Map.entry("메밀", List.of("메밀", "buckwheat", "sarrasin")),
            Map.entry("새우", List.of("새우", "shrimp", "prawn", "crevette")),
            Map.entry("게", List.of("crab", "crabe")),
            Map.entry("갑각류", List.of("갑각류", "crustacean", "crustacé")),
            Map.entry("조개류", List.of("조개", "굴", "전복", "홍합", "shellfish", "mollusc", "mollusque", "oyster", "huître")),
            Map.entry("오징어", List.of("오징어", "squid", "calmar")),
            Map.entry("고등어", List.of("고등어", "mackerel", "maquereau")),
            Map.entry("돼지고기", List.of("돼지", "pork", "porc", "lard")),
            Map.entry("쇠고기", List.of("쇠고기", "소고기", "beef", "boeuf", "bœuf")),
            Map.entry("닭고기", List.of("닭", "chicken", "poulet")),
            Map.entry("복숭아", List.of("복숭아", "peach", "pêche")),
            Map.entry("토마토", List.of("토마토", "tomato", "tomate")),
            Map.entry("아황산류", List.of("아황산", "sulfite", "sulphite", "sulfur dioxide", "anhydride sulfureux",
                    "e220", "e221", "e222", "e223", "e224", "e228"))
    );

    // 한 글자 한국어 알레르기 - 성분 이름이 이 글자로 "시작"할 때만 인정 (예: "밀", "밀(국산)", "게살", "잣")
    private static final Map<String, String> SHORT_KOREAN = Map.of("밀", "밀", "게", "게", "잣", "잣");

    private IngredientDictionary() {
    }

    /** 성분 하나(예: "lait écrémé en poudre")에 들어 있는 알레르기 표준 이름들 */
    public static Set<String> findAllergens(String ingredient) {
        // "Sans gluten", "gluten-free", "우유 무첨가"처럼 "없다"는 표시는 성분이 아니다
        if (ingredient != null && isAbsenceStatement(ingredient)) return new LinkedHashSet<>();
        Set<String> found = find(ingredient, ALLERGENS);
        String trimmed = ingredient.strip();
        SHORT_KOREAN.forEach((prefix, name) -> {
            // "밀크(우유)"는 밀이 아니다
            if (trimmed.startsWith(prefix) && !trimmed.startsWith("밀크")) found.add(name);
        });
        return found;
    }

    private static final List<String> ABSENCE_MARKERS = List.of(
            "sans ", "-free", " free", "without", "no added", "무첨가", "불포함", "없음",
            // "프리"만 쓰면 "프리미엄 우유"도 걸리므로 구체적으로
            "글루텐프리", "락토프리", "슈가프리",
            // "무MSG" 같은 마케팅 문구
            "무msg", "무 msg", "no msg");

    /** "Sans gluten", "합성보존료 무첨가", "MSG free"처럼 성분이 "없다"는 표시인지 (알레르기/유해성분 모두에 사용) */
    public static boolean isAbsenceStatement(String ingredient) {
        String text = ingredient.toLowerCase(Locale.ROOT);
        return ABSENCE_MARKERS.stream().anyMatch(text::contains);
    }

    private static Set<String> find(String ingredient, Map<String, List<String>> dictionary) {
        Set<String> found = new LinkedHashSet<>();
        if (ingredient == null || ingredient.isBlank()) return found;
        String text = ingredient.toLowerCase(Locale.ROOT);
        dictionary.forEach((standardName, aliases) -> {
            for (String alias : aliases) {
                if (text.contains(alias)) {
                    found.add(standardName);
                    break;
                }
            }
        });
        return found;
    }
}
