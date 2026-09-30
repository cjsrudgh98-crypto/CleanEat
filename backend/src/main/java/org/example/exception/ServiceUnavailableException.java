package org.example.exception;

/**
 * 메일 서버/OCR 엔진처럼 서버 쪽 기능이 잠시 동작하지 않을 때 (503).
 * 메시지는 사용자에게 그대로 보여주므로 내부 정보 없이 "무엇을 하면 되는지"를 적는다.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
