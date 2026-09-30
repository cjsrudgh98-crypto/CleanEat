package org.example.payment;

import java.util.Map;

/** 토스페이먼츠 은행 코드(두 자리) -> 은행 이름. 가상계좌 안내에 쓴다. 모르는 코드면 코드 그대로 보여준다. */
public final class BankCodes {

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("02", "KDB산업은행"), Map.entry("03", "IBK기업은행"), Map.entry("06", "KB국민은행"),
            Map.entry("07", "Sh수협은행"), Map.entry("11", "NH농협은행"), Map.entry("12", "단위농협"),
            Map.entry("20", "우리은행"), Map.entry("23", "SC제일은행"), Map.entry("27", "씨티은행"),
            Map.entry("31", "iM뱅크(대구)"), Map.entry("32", "부산은행"), Map.entry("34", "광주은행"),
            Map.entry("35", "제주은행"), Map.entry("37", "전북은행"), Map.entry("39", "경남은행"),
            Map.entry("45", "새마을금고"), Map.entry("48", "신협"), Map.entry("50", "저축은행"),
            Map.entry("54", "HSBC은행"), Map.entry("64", "산림조합"), Map.entry("71", "우체국"),
            Map.entry("81", "하나은행"), Map.entry("88", "신한은행"), Map.entry("89", "케이뱅크"),
            Map.entry("90", "카카오뱅크"), Map.entry("92", "토스뱅크"));

    private BankCodes() {
    }

    public static boolean isKnown(String code) {
        return code != null && NAMES.containsKey(code);
    }

    public static String nameOf(String code) {
        if (code == null) return null;
        return NAMES.getOrDefault(code, code);
    }
}
