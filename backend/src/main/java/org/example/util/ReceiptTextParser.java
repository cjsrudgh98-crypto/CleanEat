package org.example.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 영수증 사진을 OCR한 글자에서 "상품 줄"만 골라낸다.
 *
 * 영수증 한 줄은 보통 이런 모양이다:
 *   "001 무염구운아몬드        1    6,900"
 *   "*유기농두부 2 3,300 6,600"
 *   "8800000000059"   (상품 바코드가 다음 줄에 찍히는 영수증도 있음)
 * 상호/사업자번호/합계/카드승인 같은 줄은 버리고, 줄 앞 번호와 줄 끝 수량·가격은 떼어낸다.
 */
public final class ReceiptTextParser {

    /** 영수증에서 읽은 상품 줄 하나 - name은 가격/수량을 뗀 상품명, raw는 원래 줄 */
    public record ReceiptLine(String name, int quantity, String raw) {
    }

    public record Parsed(List<ReceiptLine> lines, Set<String> barcodes) {
    }

    public static final int MAX_LINES = 60;

    // 상품 줄이 아닌 것들 (머리말/꼬리말/합계/결제 정보)
    private static final List<String> SKIP_KEYWORDS = List.of(
            "영수증", "사업자", "대표", "전화", "주소", "매장", "점포", "계산원", "캐셔", "일시", "날짜",
            "합계", "총액", "소계", "부가세", "과세", "면세", "공급가", "세액",
            "결제", "카드", "승인", "할부", "현금", "거스름", "받을", "받은", "잔돈", "포인트", "적립", "할인", "쿠폰",
            "금액", "단가", "수량", "상품명", "품명", "교환", "환불", "감사합니다", "고객", "회원", "번호");

    // 영어는 단어 단위로만 (그냥 포함 여부로 보면 "Nutella"의 tel, "Cashew"의 cash 같은 상품명이 걸림)
    private static final Pattern SKIP_ENGLISH = Pattern.compile(
            "\\b(total|subtotal|tax|vat|cash|change|card|thank(s| you)?|tel|pos|www|http|receipt|no\\.)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern BARCODE = Pattern.compile("(?<!\\d)(\\d{8}|\\d{12,14})(?!\\d)");
    // 줄 앞 순번/기호: "001", "01.", "*", "#", "-"
    private static final Pattern LEADING_INDEX = Pattern.compile("^[\\s*#\\-·•]*(\\d{1,3}[.)]?\\s+)?[\\s*#\\-·•]*");
    // 줄 끝의 숫자 덩어리들 (수량, 단가, 금액): "1 6,900", "2 3,300 6,600", "6,900원"
    private static final Pattern TRAILING_NUMBERS = Pattern.compile("((?:\\s+[\\d,.]+(?:원)?)+)\\s*$");
    private static final Pattern LETTERS = Pattern.compile("[가-힣A-Za-z]");

    private ReceiptTextParser() {
    }

    public static Parsed parse(String text) {
        List<ReceiptLine> lines = new ArrayList<>();
        Set<String> barcodes = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return new Parsed(lines, barcodes);

        List<String> rows = text.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
        for (int i = 0; i < rows.size(); i++) {
            String line = rows.get(i);

            // 바코드가 찍힌 줄은 바코드만 모은다 (검증은 ScanService에서)
            Matcher barcode = BARCODE.matcher(line);
            boolean hasBarcode = false;
            while (barcode.find()) {
                barcodes.add(barcode.group(1));
                hasBarcode = true;
            }
            if (hasBarcode && countLetters(line) < 2) continue;

            if (isSkipLine(line)) continue;

            String name = LEADING_INDEX.matcher(withoutBarcodes(line)).replaceFirst("");
            String numbers = "";
            Matcher trailing = TRAILING_NUMBERS.matcher(name);
            if (trailing.find()) {
                numbers = trailing.group(1).trim();
                name = name.substring(0, trailing.start());
            }
            name = name.replaceAll("\\s{2,}", " ").strip();

            // 글자가 너무 적으면(OCR 잡음, 숫자뿐인 줄) 상품명이 아니다
            if (countLetters(name) < 2) continue;

            // 상품 줄 끝에는 수량·가격 숫자가 있다 - 같은 줄, 또는 (두 줄짜리 영수증이면) 바로 아랫줄에.
            // 숫자가 전혀 없는 줄은 상호·지점명 같은 머리말이다.
            // (OCR이 오른쪽 끝 가격 칸을 놓치는 경우가 있어서 수량만 남아 있어도 인정)
            if (!containsPrice(numbers) && i + 1 < rows.size()) {
                String next = withoutBarcodes(rows.get(i + 1)).strip();
                if (countLetters(next) < 2 && containsPrice(next)) numbers = next;
            }
            if (numbers.isEmpty()) continue;

            lines.add(new ReceiptLine(name, guessQuantity(numbers.split("\\s+")), line));
            if (lines.size() >= MAX_LINES) break;
        }
        return new Parsed(lines, barcodes);
    }

    // 가격처럼 보이는 숫자: "6,900", "18,000", "4500", "6,900원"
    private static final Pattern PRICE = Pattern.compile("(?<![\\d,])(\\d{1,3}(,\\d{3})+|\\d{3,7})(원)?(?![\\d,])");

    private static boolean containsPrice(String numbers) {
        return !numbers.isEmpty() && PRICE.matcher(numbers).find();
    }

    private static String withoutBarcodes(String line) {
        return BARCODE.matcher(line).replaceAll(" ");
    }

    private static boolean isSkipLine(String line) {
        for (String keyword : SKIP_KEYWORDS) {
            if (line.contains(keyword)) return true;
        }
        if (SKIP_ENGLISH.matcher(line).find()) return true;
        // 날짜/시간 줄: 2026-09-29, 2026.09.29 15:00
        return line.matches(".*\\d{2,4}[-./]\\d{1,2}[-./]\\d{1,2}.*");
    }

    /**
     * 줄 끝 숫자들 중 수량 찾기: 쉼표 없는 1~2자리 숫자(1~99) 중 첫 번째.
     * "1 6,900" -> 1, "2 3,300 6,600" -> 2, "2"(가격 칸을 OCR이 놓친 경우) -> 2, "6,900"(가격만) -> 1
     */
    private static int guessQuantity(String[] numbers) {
        for (String n : numbers) {
            if (n.matches("\\d{1,2}")) {
                int q = Integer.parseInt(n);
                if (q >= 1 && q <= 99) return q;
            }
        }
        return 1;
    }

    private static int countLetters(String s) {
        Matcher m = LETTERS.matcher(s);
        int count = 0;
        while (m.find()) count++;
        return count;
    }
}
