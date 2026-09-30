package org.example.util;

import java.util.ArrayList;
import java.util.List;

public final class IngredientTextParser {

    private IngredientTextParser() {
    }

    // 성분표 텍스트를 쉼표/슬래시/개행 기준으로 분리하고 공백을 정리한다
    public static List<String> parse(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String[] tokens = text.split("[,/\\n]+");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
