package org.example.shipping;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 택배사 목록과 배송 조회 링크. 관리자 화면의 택배사 선택지와 고객 화면/배송 안내 메일의 "배송 조회" 링크가 여기서 나온다.
 * 조회 주소는 각 택배사 공개 조회 페이지 - 택배사가 주소를 바꾸면 여기만 고치면 된다.
 */
public final class Couriers {

    private static final Map<String, String> TRACKING_URLS = new LinkedHashMap<>();

    static {
        TRACKING_URLS.put("CJ대한통운", "https://trace.cjlogistics.com/next/tracking.html?wblNo=");
        TRACKING_URLS.put("우체국택배", "https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1=");
        TRACKING_URLS.put("한진택배", "https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do?mCode=MN038&schLang=KR&wblnumText2=");
        TRACKING_URLS.put("롯데택배", "https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo=");
        TRACKING_URLS.put("로젠택배", "https://www.ilogen.com/web/personal/trace/");
        TRACKING_URLS.put("경동택배", "https://kdexp.com/service/delivery/etc/delivery.do?barcode=");
    }

    private Couriers() {
    }

    public static List<String> names() {
        return List.copyOf(TRACKING_URLS.keySet());
    }

    /** 목록에 없는 택배사(관리자가 직접 입력)거나 운송장이 없으면 null */
    public static String trackingUrl(String courier, String trackingNumber) {
        if (courier == null || trackingNumber == null || trackingNumber.isBlank()) return null;
        String base = TRACKING_URLS.get(courier.trim());
        if (base == null) return null;
        String number = trackingNumber.replaceAll("[\s-]", "");
        return base + URLEncoder.encode(number, StandardCharsets.UTF_8);
    }
}
