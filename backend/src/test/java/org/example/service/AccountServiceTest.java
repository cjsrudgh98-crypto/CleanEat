package org.example.service;

import org.example.domain.User;
import org.example.dto.account.ChangePasswordRequest;
import org.example.dto.account.UpdateNicknameRequest;
import org.example.repository.CartRepository;
import org.example.domain.EmailVerification.Purpose;
import org.example.dto.account.ChangeEmailRequest;
import org.example.dto.auth.EmailCodeResponse;
import org.example.exception.DuplicateResourceException;
import org.example.mail.EmailSender;
import org.example.repository.FavoriteRepository;
import org.example.repository.OrderRepository;
import org.example.repository.ScanHistoryRepository;
import org.example.repository.UserProfileRepository;
import org.example.repository.UserRepository;
import org.example.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ScanHistoryRepository scanHistoryRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private EmailSender emailSender;

    @Mock
    private Authentication authentication;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private org.example.repository.RestockAlertRepository restockAlertRepository;

    private AccountService accountService;

    private User user;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                userRepository, userProfileRepository, scanHistoryRepository, cartRepository, orderRepository,
                favoriteRepository, restockAlertRepository, passwordEncoder, currentUserService, emailVerificationService, emailSender,
                jwtTokenProvider);
        user = User.builder().id(1L).username("cleaneat_user").nickname("cleaneat_user").password("encoded-old").build();
        // 이메일 마스킹처럼 사용자가 필요 없는 테스트도 있어서 lenient
        lenient().when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
    }

    @Test
    void 닉네임을_변경한다() {
        UpdateNicknameRequest request = new UpdateNicknameRequest();
        request.setNickname("새닉네임");

        accountService.updateNickname(1L, request, authentication);

        assertThat(user.getNickname()).isEqualTo("새닉네임");
    }

    @Test
    void 비밀번호를_변경한다() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password123");
        when(passwordEncoder.matches("old-password", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("new-password123")).thenReturn("encoded-new");

        when(jwtTokenProvider.generateToken("cleaneat_user", 1)).thenReturn("new-token");

        String newToken = accountService.changePassword(1L, request, authentication);

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        // 이전 토큰(버전 0)은 무효가 되고, 지금 기기는 새 버전의 토큰으로 계속 로그인된다
        assertThat(user.currentTokenVersion()).isEqualTo(1);
        assertThat(newToken).isEqualTo("new-token");
    }

    @Test
    void 현재_비밀번호가_틀리면_예외를_던진다() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong");
        request.setNewPassword("new-password123");
        when(passwordEncoder.matches("wrong", "encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changePassword(1L, request, authentication))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 소셜계정은_비밀번호를_변경할_수_없다() {
        user.setProvider("google");
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("whatever");
        request.setNewPassword("new-password123");

        assertThatThrownBy(() -> accountService.changePassword(1L, request, authentication))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 회원탈퇴하면_개인데이터를_지우고_결제된_주문은_남긴_채_회원정보를_익명화한다() {
        user.setEmail("gildong@example.com");
        user.setName("홍길동");
        user.setPhone("01012345678");
        when(orderRepository.existsByUserIdAndStatusIn(eq(1L), any())).thenReturn(false);
        when(orderRepository.findByUserIdAndStatusInOrderByOrderedAtDesc(eq(1L), any())).thenReturn(Collections.emptyList());
        when(cartRepository.findByUserId(1L)).thenReturn(java.util.Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded-random");

        accountService.deleteAccount(1L, authentication);

        verify(scanHistoryRepository).deleteByUserId(1L);
        verify(userProfileRepository).deleteByUserId(1L);
        verify(favoriteRepository).deleteByUserId(1L);
        // 결제된 주문(전자상거래법상 5년 보관)이 사용자를 참조하므로 행은 지우지 않고 개인정보만 비운다
        verify(userRepository, never()).delete(any());
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getNickname()).isEqualTo(AccountService.DELETED_NICKNAME);
        assertThat(user.getUsername()).startsWith("deleted_1_");
        assertThat(user.getEmail()).isNull();
        assertThat(user.getName()).isNull();
        assertThat(user.getPhone()).isNull();
    }

    @Test
    void 배송이_끝나지_않은_주문이_있으면_탈퇴할_수_없다() {
        when(orderRepository.existsByUserIdAndStatusIn(eq(1L), any())).thenReturn(true);

        assertThatThrownBy(() -> accountService.deleteAccount(1L, authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(scanHistoryRepository, never()).deleteByUserId(any());
        assertThat(user.getDeletedAt()).isNull();
    }

    private static ChangeEmailRequest changeEmail(String email, String code) {
        ChangeEmailRequest request = new ChangeEmailRequest();
        request.setEmail(email);
        request.setCode(code);
        return request;
    }

    @Test
    void 새_이메일로_인증번호를_보낸다() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(emailVerificationService.sendCode("new@example.com", Purpose.EMAIL_CHANGE))
                .thenReturn(new EmailVerificationService.SendResult(300, "123456"));

        EmailCodeResponse response = accountService.sendEmailChangeCode(1L, " New@Example.com ", authentication);

        assertThat(response.devCode()).isEqualTo("123456");
    }

    @Test
    void 다른_계정이_쓰는_이메일로는_바꿀_수_없다() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> accountService.sendEmailChangeCode(1L, "taken@example.com", authentication))
                .isInstanceOf(DuplicateResourceException.class);
        verify(emailVerificationService, never()).sendCode(any(), any());
    }

    @Test
    void 지금_쓰는_이메일로는_바꿀_수_없다() {
        user.setEmail("same@example.com");

        assertThatThrownBy(() -> accountService.sendEmailChangeCode(1L, "SAME@example.com", authentication))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 인증번호가_맞으면_이메일을_바꾸고_예전_이메일로_알린다() {
        user.setEmail("old@example.com");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(emailVerificationService.verify("new@example.com", Purpose.EMAIL_CHANGE, "123456")).thenReturn("token");

        accountService.changeEmail(1L, changeEmail("new@example.com", "123456"), authentication);

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        verify(emailVerificationService).consume("new@example.com", Purpose.EMAIL_CHANGE, "token");
        // 트랜잭션 밖(단위 테스트)이라 바로 발송된다 - 새 이메일 전체는 적지 않는다
        verify(emailSender).send(eq("old@example.com"), any(), org.mockito.ArgumentMatchers.contains("ne***@example.com"));
    }

    @Test
    void 소셜_가입자는_처음_이메일을_등록할_수_있고_알림은_보내지_않는다() {
        user.setProvider("kakao");
        when(userRepository.existsByEmail("me@example.com")).thenReturn(false);
        when(emailVerificationService.verify("me@example.com", Purpose.EMAIL_CHANGE, "123456")).thenReturn("token");

        accountService.changeEmail(1L, changeEmail("me@example.com", "123456"), authentication);

        assertThat(user.getEmail()).isEqualTo("me@example.com");
        verify(emailSender, never()).send(any(), any(), any());
    }

    @Test
    void 인증번호가_틀리면_이메일을_바꾸지_않는다() {
        user.setEmail("old@example.com");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(emailVerificationService.verify("new@example.com", Purpose.EMAIL_CHANGE, "000000"))
                .thenThrow(new IllegalArgumentException("인증번호가 올바르지 않습니다 (남은 시도 4회)"));

        assertThatThrownBy(() -> accountService.changeEmail(1L, changeEmail("new@example.com", "000000"), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getEmail()).isEqualTo("old@example.com");
    }

    @Test
    void 알림_메일에는_새_이메일을_가려서_적는다() {
        assertThat(AccountService.maskEmail("abcdef@example.com")).isEqualTo("ab***@example.com");
        assertThat(AccountService.maskEmail("a@example.com")).isEqualTo("a***@example.com");
    }
}
