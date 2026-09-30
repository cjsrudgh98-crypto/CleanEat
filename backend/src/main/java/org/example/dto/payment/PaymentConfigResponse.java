package org.example.dto.payment;

// 프론트가 결제위젯을 띄울 때 쓰는 클라이언트 키 (공개돼도 되는 키 - 시크릿 키는 서버에만 있음)
public record PaymentConfigResponse(String clientKey) {
}
