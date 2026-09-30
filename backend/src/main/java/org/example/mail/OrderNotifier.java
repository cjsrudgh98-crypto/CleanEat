package org.example.mail;

import lombok.RequiredArgsConstructor;
import org.example.domain.Order;
import org.example.shipping.Couriers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * 주문 안내 메일 (발송 / 가상계좌 입금 확인 / 입금 기한 만료).
 * 상태 변경이 커밋된 뒤에만 보내고(롤백되면 안 보냄), 메일이 실패해도 주문 처리는 그대로 둔다.
 * 소셜 로그인 등으로 이메일이 없는 회원은 보내지 않는다 (주문 내역 화면에서 확인 가능).
 */
@Component
@RequiredArgsConstructor
public class OrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifier.class);

    private final EmailSender emailSender;

    public void notifyShipped(Order order) {
        String trackingUrl = Couriers.trackingUrl(order.getCourier(), order.getTrackingNumber());
        sendAfterCommit(order, "[CleanEat] 주문하신 상품이 발송되었습니다",
                header(order)
                        + "택배사: " + order.getCourier() + "\n"
                        + "운송장 번호: " + order.getTrackingNumber() + "\n"
                        + (trackingUrl != null ? "배송 조회: " + trackingUrl + "\n" : ""));
    }

    public void notifyDepositConfirmed(Order order) {
        sendAfterCommit(order, "[CleanEat] 입금이 확인되었습니다",
                header(order)
                        + "입금 금액: " + won(order) + "\n"
                        + "결제가 완료되어 곧 배송 준비를 시작합니다.\n");
    }

    public void notifyDepositExpired(Order order) {
        sendAfterCommit(order, "[CleanEat] 입금 기한이 지나 주문이 취소되었습니다",
                header(order)
                        + "가상계좌 입금 기한까지 입금이 확인되지 않아 주문이 자동으로 취소되었습니다.\n"
                        + "상품이 필요하시면 다시 주문해주세요.\n");
    }

    public void notifyReturnApproved(Order order) {
        sendAfterCommit(order, "[CleanEat] 반품이 완료되어 환불되었습니다",
                header(order)
                        + "환불 금액: " + won(order) + "\n"
                        + "카드 결제는 카드사에 따라 영업일 기준 3~7일 안에 취소가 반영됩니다.\n");
    }

    public void notifyReturnRejected(Order order) {
        sendAfterCommit(order, "[CleanEat] 반품 신청이 처리되지 않았습니다",
                header(order)
                        + "사유: " + order.getReturnRejectReason() + "\n"
                        + "궁금한 점은 고객센터로 문의해주세요.\n");
    }

    private static String header(Order order) {
        return "주문번호 " + order.getId() + " (" + order.getOrderName() + ")\n\n";
    }

    private static String won(Order order) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(order.netAmount()) + "원";
    }

    private void sendAfterCommit(Order order, String subject, String body) {
        String email = order.getUser() != null ? order.getUser().getEmail() : null;
        if (email == null || email.isBlank()) return;
        String text = body + "\nCleanEat 주문 내역에서도 주문 상태를 확인할 수 있습니다.";
        Long orderId = order.getId();

        Runnable send = () -> {
            try {
                emailSender.send(email, subject, text);
            } catch (RuntimeException e) {
                log.warn("주문 안내 메일 실패: orderId={} subject={} ({})", orderId, subject, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
