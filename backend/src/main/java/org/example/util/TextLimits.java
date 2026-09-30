package org.example.util;

/**
 * DB 컬럼 길이 제한. 넘는 글자를 그대로 저장하면 "Data too long" 오류로 요청 전체가 실패하므로
 * 저장 직전에 잘라낸다 (성분표가 긴 해외 제품, OCR로 읽은 긴 라벨 등).
 */
public final class TextLimits {

    public static final int RAW_INGREDIENTS = 2000;
    public static final int MATCH_LIST = 1000;
    public static final int PRODUCT_NAME = 200;

    private TextLimits() {
    }

    public static String truncate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max - 1) + "…";
    }
}
