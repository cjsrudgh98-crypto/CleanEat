package org.example.exception;

/**
 * 토스페이먼츠가 결제 승인/취소를 거절한 경우 (카드 한도 초과, 잔액 부족 등).
 * message에는 토스가 내려준 사용자용 메시지를 그대로 담는다.
 */
public class PaymentException extends RuntimeException {

    private final String code;

    public PaymentException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
