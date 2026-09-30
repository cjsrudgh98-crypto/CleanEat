package org.example.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 영수증 QR코드/바코드에서 읽은 글자를 해석한다.
 *  1) CleanEat 주문 영수증: "CLEANEAT-RECEIPT:CE-<32자리>" (URL 등 다른 글자에 섞여 있어도 CE-코드만 찾으면 됨)
 *  2) 상품 바코드 목록: 글자 안의 8/12/13/14자리 숫자를 바코드 후보로 모두 뽑는다.
 *     영수증 번호·전화번호·날짜 같은 숫자가 섞여 있을 수 있어서, 후보 중 실제로 쓸 것은 ScanService가
 *     "바코드 검증 숫자가 맞거나(isValidGtin) 우리 DB에 있는 바코드"로 한 번 더 거른다.
 *     "8801234567893*2", "8801234567893 x2" 처럼 수량이 붙어 있으면 수량으로 읽는다.
 */
public final class ReceiptCodeParser {

    public static final String CLEANEAT_PREFIX = "CLEANEAT-RECEIPT:";
    public static final int MAX_BARCODES = 30;

    private static final Pattern ORDER_CODE = Pattern.compile("CE-[0-9a-f]{32}");
    private static final Pattern BARCODE_WITH_QTY =
            Pattern.compile("(?<!\\d)(\\d{8}|\\d{12,14})(?!\\d)(?:\\s*[x×*]\\s*(\\d{1,3}))?", Pattern.CASE_INSENSITIVE);

    public sealed interface Parsed permits OrderReceipt, BarcodeList, Unknown {
    }

    public record OrderReceipt(String orderCode) implements Parsed {
    }

    /** 바코드 -> 수량 (같은 바코드가 여러 번 나오면 수량을 더함, 등장 순서 유지) */
    public record BarcodeList(Map<String, Integer> quantities) implements Parsed {
    }

    public record Unknown() implements Parsed {
    }

    private ReceiptCodeParser() {
    }

    public static Parsed parse(String raw) {
        if (raw == null || raw.isBlank()) return new Unknown();

        Matcher order = ORDER_CODE.matcher(raw);
        if (order.find()) return new OrderReceipt(order.group());

        Map<String, Integer> quantities = new LinkedHashMap<>();
        Matcher m = BARCODE_WITH_QTY.matcher(raw);
        while (m.find() && quantities.size() < MAX_BARCODES) {
            String digits = m.group(1);
            int qty = m.group(2) != null ? Math.max(1, Integer.parseInt(m.group(2))) : 1;
            quantities.merge(digits, qty, Integer::sum);
        }
        return quantities.isEmpty() ? new Unknown() : new BarcodeList(quantities);
    }

    /** GS1 검증 숫자 확인 (EAN-8, UPC-A, EAN-13, GTIN-14 공통 규칙) */
    public static boolean isValidGtin(String digits) {
        int len = digits.length();
        if (len != 8 && len != 12 && len != 13 && len != 14) return false;
        int sum = 0;
        // 검증 숫자 바로 앞자리부터 왼쪽으로 3,1,3,1... 가중치
        for (int i = len - 2, weight = 3; i >= 0; i--, weight = 4 - weight) {
            sum += (digits.charAt(i) - '0') * weight;
        }
        int check = (10 - (sum % 10)) % 10;
        return check == digits.charAt(len - 1) - '0';
    }

    /** 영수증 QR/바코드에 넣을 문자열 */
    public static String cleanEatReceiptCode(String tossOrderId) {
        return CLEANEAT_PREFIX + tossOrderId;
    }
}
