package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.RestockAlert;
import org.example.domain.StoreListing;
import org.example.domain.User;
import org.example.exception.ResourceNotFoundException;
import org.example.mail.AfterCommit;
import org.example.mail.EmailSender;
import org.example.repository.RestockAlertRepository;
import org.example.repository.StoreListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 품절 상품 재입고 알림.
 *  - 신청: 품절일 때만, 알림 받을 이메일이 있는 회원만 (소셜 가입자는 마이페이지에서 이메일 등록)
 *  - 발송: 관리자가 재고를 채우면 바로 (notifyIfRestocked), 그 밖에 취소/반품으로 재고가 돌아온 경우는
 *          RestockAlertScheduler가 주기적으로 확인 (sendRestocked). 보내면 신청을 지운다 (한 번만 알림).
 */
@Service
@RequiredArgsConstructor
public class RestockAlertService {

    private static final Logger log = LoggerFactory.getLogger(RestockAlertService.class);

    private final RestockAlertRepository restockAlertRepository;
    private final StoreListingRepository storeListingRepository;
    private final CurrentUserService currentUserService;
    private final EmailSender emailSender;

    @Transactional
    public void subscribe(Long productId, Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        StoreListing listing = storeListingRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("판매 중인 상품이 아닙니다: productId=" + productId));
        if (restockAlertRepository.findByUserIdAndStoreListingProductId(user.getId(), productId).isPresent()) {
            return; // 이미 신청함 - 두 번 눌러도 그대로
        }
        if (listing.getStock() > 0) {
            throw new IllegalArgumentException("지금 구매할 수 있는 상품입니다");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("재입고 알림은 메일로 보내드려요. 마이페이지에서 이메일을 먼저 등록해주세요");
        }
        restockAlertRepository.save(RestockAlert.builder().user(user).storeListing(listing).build());
    }

    @Transactional
    public void unsubscribe(Long productId, Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        restockAlertRepository.findByUserIdAndStoreListingProductId(user.getId(), productId)
                .ifPresent(restockAlertRepository::delete);
    }

    /** 관리자 재고 조정 직후 - 품절이던 상품에 재고가 생겼으면 바로 알린다 (같은 트랜잭션, 메일은 커밋 후) */
    @Transactional
    public int notifyIfRestocked(StoreListing listing, int stockBefore) {
        if (stockBefore > 0 || listing.getStock() <= 0) return 0;
        return send(restockAlertRepository.findByListingIdWithUser(listing.getId()), listing);
    }

    /** 주기 확인 - 재고가 다시 생긴 모든 상품의 신청자에게 보낸다 (취소/반품으로 재고가 돌아온 경우 등) */
    @Transactional
    public int sendRestocked() {
        List<RestockAlert> alerts = restockAlertRepository.findRestocked();
        alerts.stream().map(RestockAlert::getStoreListing).distinct()
                .forEach(listing -> send(alerts.stream().filter(a -> a.getStoreListing() == listing).toList(), listing));
        return alerts.size();
    }

    private int send(List<RestockAlert> alerts, StoreListing listing) {
        String productName = listing.getProduct().getName();
        for (RestockAlert alert : alerts) {
            String email = alert.getUser().getEmail();
            if (email != null && !email.isBlank()) {
                AfterCommit.run(() -> emailSender.send(email, "[CleanEat] " + productName + " 재입고 알림",
                        "알림을 신청하신 " + productName + "이(가) 다시 입고되었습니다.\n"
                                + "수량이 한정되어 있으니 CleanEat 상품 페이지에서 확인해주세요.\n\n"
                                + "이 알림은 한 번만 보내드립니다."));
            }
        }
        restockAlertRepository.deleteAll(alerts);
        if (!alerts.isEmpty()) log.info("재입고 알림 {}건 발송: {}", alerts.size(), productName);
        return alerts.size();
    }
}
