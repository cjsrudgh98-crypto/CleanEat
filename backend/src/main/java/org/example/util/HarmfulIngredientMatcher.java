package org.example.util;

import org.example.domain.Ingredient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 성분표 글자에서 유해성분 사전(DB ingredients + 별칭)의 성분을 찾는다.
 *
 * 표기 규칙
 *  - 한국어 별칭: 글자 사이 띄어쓰기를 무시한다 ("황색 제4호" = "황색제4호")
 *  - E-번호: 앞뒤가 글자/숫자가 아니어야 한다 ("E102"는 "E1020"에 안 걸림, "E 102"·"E-102"도 인식)
 *  - 4글자 이하 영문 약어(MSG, BHA, EDTA 등): 단어로 떨어져 있어야 한다
 *  - 그 밖의 영문: 단어 시작에서만 ("artificial colo"는 colour/color 모두), 숫자로 끝나면 뒤에 숫자가 오면 안 됨 ("red 3" ≠ "red 33")
 *  - 겹치는 경우 긴 쪽만 인정: "아질산나트륨" 안의 "질산나트륨", "안식향산나트륨" 안의 "안식향산"은 따로 세지 않는다
 *  - "합성보존료 무첨가", "sans conservateur", "MSG free"처럼 "없다"는 표시는 성분이 아니다
 */
public final class HarmfulIngredientMatcher {

    private static final Pattern E_NUMBER_ALIAS = Pattern.compile("e\\d{3,4}[a-z]?");
    // "E 102", "E-102" -> "e102"
    private static final Pattern E_NUMBER_IN_TEXT = Pattern.compile("(?<![a-z0-9])e[\\s\\-]+(?=\\d{3})");

    private record Entry(String name, List<Pattern> patterns) {
    }

    private record Hit(String name, int start, int end) {
        int length() {
            return end - start;
        }

        boolean inside(Hit other) {
            return other.start <= start && end <= other.end;
        }
    }

    private final List<Entry> entries;

    private HarmfulIngredientMatcher(List<Entry> entries) {
        this.entries = entries;
    }

    /** 사용 중인 성분만으로 만든다 (사용 중지한 성분은 찾지 않음) */
    public static HarmfulIngredientMatcher of(Collection<Ingredient> ingredients) {
        List<Entry> entries = ingredients.stream()
                .filter(Ingredient::isActive)
                .map(i -> {
                    Set<String> aliases = new LinkedHashSet<>();
                    aliases.add(i.getName());
                    if (i.getAliases() != null) aliases.addAll(i.getAliases());
                    List<Pattern> patterns = aliases.stream()
                            .filter(a -> a != null && !a.isBlank())
                            .map(HarmfulIngredientMatcher::compile)
                            .toList();
                    return new Entry(i.getName(), patterns);
                })
                .toList();
        return new HarmfulIngredientMatcher(entries);
    }

    /** 성분 하나(예: "Sodium Benzoate (E211)")에 들어 있는 유해성분 이름들 (나온 순서) */
    public Set<String> find(String ingredient) {
        Set<String> result = new LinkedHashSet<>();
        if (ingredient == null || ingredient.isBlank() || IngredientDictionary.isAbsenceStatement(ingredient)) return result;
        String text = normalize(ingredient);

        List<Hit> hits = new ArrayList<>();
        for (Entry entry : entries) {
            for (Pattern pattern : entry.patterns()) {
                Matcher m = pattern.matcher(text);
                while (m.find()) hits.add(new Hit(entry.name(), m.start(), m.end()));
            }
        }
        // 긴 것부터 받아들이고, 다른 성분의 더 긴 표기 안에 완전히 들어가는 짧은 표기는 버린다
        hits.sort(Comparator.comparingInt(Hit::length).reversed());
        List<Hit> accepted = new ArrayList<>();
        for (Hit hit : hits) {
            boolean shadowed = accepted.stream().anyMatch(a -> !a.name().equals(hit.name()) && hit.inside(a));
            if (!shadowed) accepted.add(hit);
        }
        accepted.stream().sorted(Comparator.comparingInt(Hit::start)).forEach(h -> result.add(h.name()));
        return result;
    }

    static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return E_NUMBER_IN_TEXT.matcher(lower).replaceAll("e");
    }

    static Pattern compile(String rawAlias) {
        String alias = rawAlias.toLowerCase(Locale.ROOT).strip();
        boolean hangul = alias.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL);
        if (hangul) {
            String body = alias.replaceAll("\\s+", "").codePoints()
                    .mapToObj(c -> Pattern.quote(new String(Character.toChars(c))))
                    .collect(Collectors.joining("\\s*"));
            return Pattern.compile(body);
        }
        String body = Arrays.stream(alias.split("\\s+")).map(Pattern::quote).collect(Collectors.joining("\\s+"));
        if (E_NUMBER_ALIAS.matcher(alias).matches()) {
            return Pattern.compile("(?<![a-z0-9])" + body + "(?![a-z0-9])");
        }
        if (alias.length() <= 4) {
            return Pattern.compile("(?<![a-z])" + body + "(?![a-z])");
        }
        String tail = Character.isDigit(alias.charAt(alias.length() - 1)) ? "(?![0-9])" : "";
        return Pattern.compile("(?<![a-z])" + body + tail);
    }
}
