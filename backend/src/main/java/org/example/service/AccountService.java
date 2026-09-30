package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.EmailVerification.Purpose;
import org.example.domain.Order;
import org.example.domain.OrderStatus;
import org.example.domain.User;
import org.example.dto.account.ChangeEmailRequest;
import org.example.dto.account.ChangePasswordRequest;
import org.example.dto.auth.EmailCodeResponse;
import org.example.exception.DuplicateResourceException;
import org.example.mail.AfterCommit;
import org.example.mail.EmailSender;
import org.example.dto.account.UpdateNicknameRequest;
import org.example.repository.CartRepository;
import org.example.repository.FavoriteRepository;
import org.example.repository.OrderRepository;
import org.example.repository.RestockAlertRepository;
import org.example.repository.ScanHistoryRepository;
import org.example.repository.UserProfileRepository;
import org.example.repository.UserRepository;
import org.example.security.JwtTokenProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    public static final String DELETED_NICKNAME = "탈퇴한 회원";

    // 이 상태의 주문이 있으면 탈퇴할 수 없다 (결제는 됐는데 배송이 안 끝난 주문 - 탈퇴하면 환불/배송 처리가 꼬임)
    private static final Set<OrderStatus> IN_PROGRESS = EnumSet.copyOf(
            Arrays.stream(OrderStatus.values()).filter(OrderStatus::isInProgress).toList());
    // 실제 결제가 없었던 주문서 - 탈퇴 시 그냥 지운다
    private static final Set<OrderStatus> UNPAID = EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED);

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ScanHistoryRepository scanHistoryRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final FavoriteRepository favoriteRepository;
    private final RestockAlertRepository restockAlertRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final EmailVerificationService emailVerificationService;
    private final EmailSender emailSender;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void updateNickname(Long userId, UpdateNicknameRequest request, Authentication authentication) {
        currentUserService.assertOwnership(authentication, userId);
        User user = currentUserService.getCurrentUser(authentication);
        user.setNickname(request.getNickname().trim());
    }

    /**
     * 비밀번호 변경. 이전에 발급된 로그인 토큰은 모두 무효가 되고(다른 기기 로그아웃),
     * 지금 쓰는 기기가 계속 로그인돼 있도록 새 토큰을 돌려준다.
     */
    @Transactional
    public String changePassword(Long userId, ChangePasswordRequest request, Authentication authentication) {
        currentUserService.assertOwnership(authentication, userId);
        User user = currentUserService.getCurrentUser(authentication);

        if (user.getProvider() != null) {
            throw new IllegalArgumentException("소셜 로그인 계정은 비밀번호를 변경할 수 없습니다");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.invalidateTokens();
        return jwtTokenProvider.generateToken(user.getUsername(), user.currentTokenVersion());
    }

    /** 이메일 변경 1단계: 새 이메일로 인증번호 발송 (이미 다른 계정이 쓰는 이메일이면 바로 알려준다) */
    @Transactional
    public EmailCodeResponse sendEmailChangeCode(Long userId, String rawEmail, Authentication authentication) {
        currentUserService.assertOwnership(authentication, userId);
        User user = currentUserService.getCurrentUser(authentication);
        String email = normalizeEmail(rawEmail);
        checkNewEmail(user, email);
        EmailVerificationService.SendResult result = emailVerificationService.sendCode(email, Purpose.EMAIL_CHANGE);
        return new EmailCodeResponse("새 이메일로 인증번호를 보냈습니다", result.expiresInSeconds(), result.devCode());
    }

    /**
     * 이메일 변경 2단계: 인증번호가 맞으면 바로 바꾼다. 예전 이메일이 있으면 변경 사실을 알려준다
     * (본인이 바꾼 게 아니면 알아챌 수 있게).
     */
    @Transactional
    public void changeEmail(Long userId, ChangeEmailRequest request, Authentication authentication) {
        currentUserService.assertOwnership(authentication, userId);
        User user = currentUserService.getCurrentUser(authentication);
        String email = normalizeEmail(request.getEmail());
        checkNewEmail(user, email);

        String token = emailVerificationService.verify(email, Purpose.EMAIL_CHANGE, request.getCode());
        emailVerificationService.consume(email, Purpose.EMAIL_CHANGE, token);

        String oldEmail = user.getEmail();
        user.setEmail(email);
        if (oldEmail != null) {
            AfterCommit.run(() -> emailSender.send(oldEmail, "[CleanEat] 계정 이메일이 변경되었습니다",
                    "CleanEat 계정(" + user.getUsername() + ")의 이메일이 " + maskEmail(email) + "(으)로 변경되었습니다.\n\n"
                            + "본인이 변경하지 않았다면 비밀번호를 바꾸고 고객센터로 문의해주세요."));
        }
    }

    private void checkNewEmail(User user, String email) {
        if (email.equals(user.getEmail())) {
            throw new IllegalArgumentException("지금 사용 중인 이메일과 같습니다");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("이미 다른 계정에서 사용 중인 이메일입니다");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // 알림 메일에 새 이메일 전체를 적지 않는다 (예: ab***@example.com)
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }

    /**
     * 회원 탈퇴.
     *  - 배송이 끝나지 않은 주문이 있으면 거절 (먼저 취소하거나 배송 완료 후 탈퇴)
     *  - 검사 기록/식단 설정/장바구니/찜/결제 안 된 주문서는 삭제
     *  - 결제된 주문 기록은 전자상거래법상 보관해야 하므로 남기고, 회원 정보(이름·이메일·연락처 등)만 지워 익명화한다
     *    (주문서의 배송지/수령인 정보는 거래 기록의 일부라 함께 보관)
     */
    @Transactional
    public void deleteAccount(Long userId, Authentication authentication) {
        currentUserService.assertOwnership(authentication, userId);
        User user = currentUserService.getCurrentUser(authentication);

        if (orderRepository.existsByUserIdAndStatusIn(userId, IN_PROGRESS)) {
            throw new IllegalArgumentException(
                    "배송이 끝나지 않은 주문이 있어 탈퇴할 수 없습니다. 주문을 취소하거나 배송 완료 후 다시 시도해주세요");
        }

        scanHistoryRepository.deleteByUserId(userId);
        userProfileRepository.deleteByUserId(userId);
        cartRepository.findByUserId(userId).ifPresent(cartRepository::delete);
        favoriteRepository.deleteByUserId(userId);
        restockAlertRepository.deleteByUserId(userId);
        List<Order> unpaid = orderRepository.findByUserIdAndStatusInOrderByOrderedAtDesc(userId, UNPAID);
        orderRepository.deleteAll(unpaid);

        // 같은 아이디/이메일/소셜 계정으로 다시 가입할 수 있게 고유 값들을 비운다
        user.setUsername("deleted_" + user.getId() + "_" + UUID.randomUUID().toString().substring(0, 8));
        user.setNickname(DELETED_NICKNAME);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setName(null);
        user.setEmail(null);
        user.setPhone(null);
        user.setBirthDate(null);
        user.setProvider(null);
        user.setProviderId(null);
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        user.setDeletedAt(LocalDateTime.now());
        user.invalidateTokens();
    }
}
