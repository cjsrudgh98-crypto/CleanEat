package org.example.service;

import org.example.config.AdminAccounts;
import org.example.domain.EmailVerification.Purpose;
import org.example.domain.User;
import org.example.dto.auth.AuthResponse;
import org.example.dto.auth.EmailCodeResponse;
import org.example.dto.auth.FindUsernameRequest;
import org.example.dto.auth.FindUsernameResponse;
import org.example.dto.auth.LoginRequest;
import org.example.dto.auth.PasswordResetCodeRequest;
import org.example.dto.auth.PasswordResetConfirmRequest;
import org.example.dto.auth.PasswordResetVerifyRequest;
import org.example.dto.auth.PasswordResetVerifyResponse;
import org.example.dto.auth.RegisterRequest;
import org.example.exception.DuplicateResourceException;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.CartRepository;
import org.example.repository.UserProfileRepository;
import org.example.repository.UserRepository;
import org.example.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private AdminAccounts adminAccounts;

    @Mock
    private CartRepository cartRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, userProfileRepository, cartRepository, passwordEncoder, authenticationManager,
                jwtTokenProvider, emailVerificationService, adminAccounts);
    }

    private static RegisterRequest registerRequest(String username) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword("password123!");
        request.setName("홍길동");
        request.setNickname("길동이");
        request.setEmail(" Gildong@Example.com ");
        request.setPhone("010-1234-5678");
        request.setBirthDate(LocalDate.of(1998, 1, 1));
        request.setAgreeTerms(true);
        request.setEmailVerificationToken("verified-token");
        return request;
    }

    @Test
    void 회원가입에_성공하면_토큰과_사용자정보를_반환한다() {
        RegisterRequest request = registerRequest("cleaneat_user");

        when(userRepository.existsByUsername("cleaneat_user")).thenReturn(false);
        when(userRepository.existsByEmail("gildong@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123!")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(jwtTokenProvider.generateToken("cleaneat_user", 0)).thenReturn("test-jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("test-jwt-token");
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("cleaneat_user");
        assertThat(response.getNickname()).isEqualTo("길동이");
        assertThat(response.getRole()).isEqualTo("USER");
        // 인증번호로 확인한 이메일 토큰을 소비해야 가입된다 (재사용 방지)
        verify(emailVerificationService).consume("gildong@example.com", Purpose.REGISTER, "verified-token");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        // 이메일은 소문자/공백제거, 휴대폰 번호는 숫자만 남겨서 저장해야 찾기 기능에서 일관되게 비교된다
        assertThat(captor.getValue().getEmail()).isEqualTo("gildong@example.com");
        assertThat(captor.getValue().getPhone()).isEqualTo("01012345678");
        verify(userProfileRepository).save(any());
        // 장바구니도 가입할 때 같이 만든다 (처음 쓸 때 동시에 만들다 충돌하지 않게)
        verify(cartRepository).save(any());
    }

    @Test
    void 이미_존재하는_아이디면_예외를_던진다() {
        RegisterRequest request = registerRequest("existing_user");

        when(userRepository.existsByUsername("existing_user")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void 소셜_탈퇴_계정_형식의_아이디로는_가입할_수_없다() {
        // kakao_ + 10자리 회원번호는 아이디 규칙(영문 소문자/숫자/밑줄 4~20자)을 통과하므로 서비스에서 막아야 한다
        for (String username : List.of("kakao_1234567890", "google_1", "naver_abc", "deleted_12_ab")) {
            RegisterRequest request = registerRequest(username);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용할 수 없는 아이디");
        }
        verify(userRepository, never()).save(any());
        verify(emailVerificationService, never()).consume(any(), any(), any());
    }

    @Test
    void 이미_가입된_이메일이면_예외를_던진다() {
        RegisterRequest request = registerRequest("new_user");

        when(userRepository.existsByUsername("new_user")).thenReturn(false);
        when(userRepository.existsByEmail("gildong@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void 로그인에_성공하면_토큰을_반환한다() {
        LoginRequest request = new LoginRequest();
        request.setUsername("cleaneat_user");
        request.setPassword("password123");

        User user = User.builder().id(1L).username("cleaneat_user").password("encoded-password").build();
        when(userRepository.findByUsername("cleaneat_user")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken("cleaneat_user", 0)).thenReturn("test-jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("test-jwt-token");
        assertThat(response.getUserId()).isEqualTo(1L);
    }

    @Test
    void 이름과_이메일이_일치하면_가려진_아이디를_반환한다() {
        FindUsernameRequest request = new FindUsernameRequest();
        request.setName("홍길동");
        request.setEmail("GILDONG@example.com");

        User user = User.builder().id(1L).username("cleaneat_user").build();
        when(userRepository.findByNameAndEmail("홍길동", "gildong@example.com")).thenReturn(Optional.of(user));

        FindUsernameResponse response = authService.findUsername(request);

        assertThat(response.getMaskedUsername()).isEqualTo("cl**********r");
    }

    @Test
    void 일치하는_계정이_없으면_아이디찾기에서_예외를_던진다() {
        FindUsernameRequest request = new FindUsernameRequest();
        request.setName("홍길동");
        request.setEmail("nobody@example.com");

        when(userRepository.findByNameAndEmail(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.findUsername(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 아이디_마스킹은_짧은_아이디도_처리한다() {
        assertThat(AuthService.maskUsername("abcd")).isEqualTo("a***");
        assertThat(AuthService.maskUsername("abcde")).isEqualTo("ab**e");
    }

    @Test
    void 이메일_인증번호가_맞으면_재설정토큰을_발급하고_그_토큰으로_비밀번호를_바꿀_수_있다() {
        User user = User.builder().id(1L).username("cleaneat_user").password("old")
                .email("gildong@example.com").phone("01012345678").build();
        when(userRepository.findByUsername("cleaneat_user")).thenReturn(Optional.of(user));
        when(emailVerificationService.verify("gildong@example.com", Purpose.PASSWORD_RESET, "123456"))
                .thenReturn("email-verified-token");

        PasswordResetVerifyRequest verify = new PasswordResetVerifyRequest();
        verify.setUsername("cleaneat_user");
        verify.setEmail("Gildong@Example.com");
        verify.setCode("123456");

        PasswordResetVerifyResponse issued = authService.verifyForPasswordReset(verify);

        assertThat(issued.getResetToken()).isNotBlank();
        // DB에는 원문 토큰이 아니라 해시만 저장돼야 한다
        assertThat(user.getPasswordResetTokenHash()).isNotEqualTo(issued.getResetToken()).hasSize(64);

        when(userRepository.findByPasswordResetTokenHash(user.getPasswordResetTokenHash())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword1!")).thenReturn("encoded-new");

        PasswordResetConfirmRequest confirm = new PasswordResetConfirmRequest();
        confirm.setResetToken(issued.getResetToken());
        confirm.setNewPassword("newPassword1!");
        authService.resetPassword(confirm);

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        assertThat(user.getPasswordResetTokenHash()).isNull();
        // 재설정하면 다른 기기의 로그인도 모두 끊긴다
        assertThat(user.currentTokenVersion()).isEqualTo(1);
    }

    @Test
    void 이메일이_가입정보와_다르면_재설정토큰을_발급하지_않는다() {
        User user = User.builder().id(1L).username("cleaneat_user")
                .email("gildong@example.com").phone("01012345678").build();
        when(userRepository.findByUsername("cleaneat_user")).thenReturn(Optional.of(user));

        PasswordResetVerifyRequest verify = new PasswordResetVerifyRequest();
        verify.setUsername("cleaneat_user");
        verify.setEmail("other@example.com");
        verify.setCode("123456");

        assertThatThrownBy(() -> authService.verifyForPasswordReset(verify))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(user.getPasswordResetTokenHash()).isNull();
        verify(emailVerificationService, never()).verify(any(), any(), any());
    }

    @Test
    void 인증번호가_틀리면_재설정토큰을_발급하지_않는다() {
        User user = User.builder().id(1L).username("cleaneat_user").email("gildong@example.com").build();
        when(userRepository.findByUsername("cleaneat_user")).thenReturn(Optional.of(user));
        when(emailVerificationService.verify("gildong@example.com", Purpose.PASSWORD_RESET, "000000"))
                .thenThrow(new IllegalArgumentException("인증번호가 올바르지 않습니다 (남은 시도 4회)"));

        PasswordResetVerifyRequest verify = new PasswordResetVerifyRequest();
        verify.setUsername("cleaneat_user");
        verify.setEmail("gildong@example.com");
        verify.setCode("000000");

        assertThatThrownBy(() -> authService.verifyForPasswordReset(verify))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getPasswordResetTokenHash()).isNull();
    }

    @Test
    void 아이디와_이메일이_맞지_않으면_인증번호를_보내지_않지만_같은_응답을_준다() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        PasswordResetCodeRequest request = new PasswordResetCodeRequest();
        request.setUsername("nobody");
        request.setEmail("nobody@example.com");

        EmailCodeResponse response = authService.sendPasswordResetCode(request);

        // 계정 존재 여부를 알 수 없게 메시지는 같고, 메일은 보내지 않는다
        assertThat(response.message()).startsWith("입력하신 정보가 맞다면");
        assertThat(response.devCode()).isNull();
        verify(emailVerificationService, never()).sendCode(any(), any());
    }

    @Test
    void 만료된_재설정토큰으로는_비밀번호를_바꿀_수_없다() {
        User user = User.builder().id(1L).username("cleaneat_user").password("old")
                .passwordResetTokenHash("hash").passwordResetExpiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(userRepository.findByPasswordResetTokenHash(anyString())).thenReturn(Optional.of(user));

        PasswordResetConfirmRequest confirm = new PasswordResetConfirmRequest();
        confirm.setResetToken("some-token");
        confirm.setNewPassword("newPassword1!");

        assertThatThrownBy(() -> authService.resetPassword(confirm))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getPassword()).isEqualTo("old");
    }

    @Test
    void 처음_소셜로그인하면_계정과_프로필을_새로_만든다() {
        when(userRepository.findByProviderAndProviderId("google", "1000")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded-random-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });
        when(jwtTokenProvider.generateToken("google_1000", 0)).thenReturn("test-jwt-token");

        AuthResponse response = authService.oauthLogin("google", "1000", "홍길동");

        assertThat(response.getToken()).isEqualTo("test-jwt-token");
        assertThat(response.getUserId()).isEqualTo(10L);
        assertThat(response.getUsername()).isEqualTo("google_1000");
        assertThat(response.getNickname()).isEqualTo("홍길동");
        verify(userProfileRepository).save(any());
    }

    @Test
    void 이미_연결된_소셜계정이면_새로_만들지_않고_기존_계정으로_로그인한다() {
        User existingUser = User.builder()
                .id(5L).username("kakao_2000").provider("kakao").providerId("2000").build();
        when(userRepository.findByProviderAndProviderId("kakao", "2000")).thenReturn(Optional.of(existingUser));
        when(jwtTokenProvider.generateToken("kakao_2000", 0)).thenReturn("test-jwt-token");

        AuthResponse response = authService.oauthLogin("kakao", "2000", null);

        assertThat(response.getUserId()).isEqualTo(5L);
        verify(userRepository, never()).save(any());
        verify(userProfileRepository, never()).save(any());
    }
}
