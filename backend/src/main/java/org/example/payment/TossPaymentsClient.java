package org.example.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.exception.ExternalApiException;
import org.example.exception.PaymentException;
import org.example.util.Hashing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 토스페이먼츠 결제 승인/취소 API 호출.
 * 시크릿 키는 서버에만 두고 Basic 인증(시크릿키 + ":" 를 Base64)으로 보낸다 - 프론트로 절대 내려보내지 않는다.
 * https://docs.tosspayments.com/reference#결제-승인
 */
@Component
public class TossPaymentsClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TossPaymentsClient(RestClient.Builder builder,
                              ObjectMapper objectMapper,
                              @Value("${app.toss.secret-key}") String secretKey,
                              @Value("${app.toss.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        // 결제 승인은 카드사 응답을 기다리므로 넉넉하게 (토스 권장 최대 60초)
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        String basicToken = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicToken)
                .build();
        this.objectMapper = objectMapper;
    }

    public TossPayment confirm(String paymentKey, String orderId, BigDecimal amount) {
        return post("/v1/payments/confirm",
                // 같은 주문을 두 번 승인 요청해도(새로고침 등) 토스가 한 번만 처리하도록
                orderId,
                Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount));
    }

    /** 가상계좌 결제를 입금 후에 취소할 때 환불받을 계좌 (bank는 두 자리 은행 코드) */
    public record RefundAccount(String bank, String accountNumber, String holderName) {
    }

    /**
     * 결제 취소(환불). refundAccount는 입금이 끝난 가상계좌 결제에서만 필요하고 나머지는 null.
     * 멱등키에 환불 계좌를 넣는다 - 계좌를 잘못 입력해서 거절된 뒤 고쳐서 다시 요청하면 새 요청으로 처리돼야 한다
     * (같은 키면 토스가 처음 응답을 그대로 돌려줌).
     */
    /**
     * 부분 취소 - cancelAmount만큼만 환불한다. 멱등키에 줄 번호(itemKey)를 넣어 상품마다 따로 처리되게 한다
     * (같은 키면 토스가 첫 응답을 돌려주므로, 두 상품을 차례로 취소할 때 키가 같으면 두 번째가 무시됨).
     */
    public TossPayment cancelPartial(String paymentKey, String cancelReason, BigDecimal cancelAmount,
                                     RefundAccount refundAccount, String itemKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancelReason", cancelReason);
        body.put("cancelAmount", cancelAmount);
        if (refundAccount != null) {
            body.put("refundReceiveAccount", Map.of(
                    "bank", refundAccount.bank(),
                    "accountNumber", refundAccount.accountNumber(),
                    "holderName", refundAccount.holderName()));
        }
        return post("/v1/payments/" + paymentKey + "/cancel", "cancel-" + paymentKey + "-" + itemKey, body);
    }

    public TossPayment cancel(String paymentKey, String cancelReason, RefundAccount refundAccount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancelReason", cancelReason);
        String idempotencyKey = "cancel-" + paymentKey;
        if (refundAccount != null) {
            body.put("refundReceiveAccount", Map.of(
                    "bank", refundAccount.bank(),
                    "accountNumber", refundAccount.accountNumber(),
                    "holderName", refundAccount.holderName()));
            idempotencyKey += "-" + Hashing.sha256(refundAccount.bank() + ":" + refundAccount.accountNumber() + ":"
                    + refundAccount.holderName()).substring(0, 16);
        }
        return post("/v1/payments/" + paymentKey + "/cancel", idempotencyKey, body);
    }

    /** 주문번호로 현재 결제 상태 조회 - 웹훅 내용을 그대로 믿지 않고 토스에 직접 확인할 때 쓴다 */
    public TossPayment getByOrderId(String orderId) {
        try {
            return restClient.get()
                    .uri("/v1/payments/orders/{orderId}", orderId)
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, response) -> {
                        TossError error = readError(response.getBody().readAllBytes());
                        throw new PaymentException(error.code(), error.message());
                    })
                    .body(TossPayment.class);
        } catch (ResourceAccessException e) {
            throw new ExternalApiException("결제 서버에 연결하지 못했습니다", e);
        }
    }

    private TossPayment post(String path, String idempotencyKey, Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, response) -> {
                        TossError error = readError(response.getBody().readAllBytes());
                        throw new PaymentException(error.code(), error.message());
                    })
                    .body(TossPayment.class);
        } catch (ResourceAccessException e) {
            throw new ExternalApiException("결제 서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요", e);
        }
    }

    private TossError readError(byte[] body) {
        try {
            TossError error = objectMapper.readValue(body, TossError.class);
            if (error.message() != null) return error;
        } catch (IOException ignored) {
            // 형식이 다르면 아래 기본 메시지 사용
        }
        return new TossError("UNKNOWN", "결제 처리 중 오류가 발생했습니다");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * @param secret 가상계좌 결제에서만 오는 값. 입금 웹훅(DEPOSIT_CALLBACK)에 같은 값이 실려 오므로, 저장해 뒀다가
     *               웹훅이 진짜 토스에서 온 것인지 확인하는 데 쓴다
     */
    public record TossPayment(String paymentKey, String orderId, String status, String method,
                              BigDecimal totalAmount, String approvedAt, Receipt receipt,
                              VirtualAccount virtualAccount, String secret) {

        // 기존 코드/테스트 호환 (가상계좌 정보 없는 결제)
        public TossPayment(String paymentKey, String orderId, String status, String method,
                           BigDecimal totalAmount, String approvedAt, Receipt receipt) {
            this(paymentKey, orderId, status, method, totalAmount, approvedAt, receipt, null, null);
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Receipt(String url) {}

        /** bankCode는 두 자리 은행 코드 (예: "20" 우리은행), dueDate는 입금 기한 */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record VirtualAccount(String accountNumber, String bankCode, String customerName, String dueDate) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossError(String code, String message) {}
}
