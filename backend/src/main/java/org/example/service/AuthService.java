package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.config.AdminAccounts;
import org.example.domain.Cart;
import org.example.domain.EmailVerification.Purpose;
import org.example.domain.Role;
import org.example.domain.User;
import org.example.domain.UserProfile;
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
import org.example.util.Hashing;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(10);
    private static final String IDENTITY_MISMATCH_MESSAGE = "입력하신 정보와 일치하는 계정이 없습니다";
    // 소셜 로그인 계정(google_<id> 등)과 탈퇴 계정(deleted_...)이 자동으로 쓰는 아이디 형식.
    // 로컬 가입으로 미리 선점하면 그 소셜 사용자가 로그인할 수 없게 되고, 관리자 목록(ADMIN_USERNAMES)에 있는
    // 소셜 아이디라면 관리자 권한까지 가져가므로 막는다.
    private static final List<String> RESERVED_USERNAME_PREFIXES = List.of("google_", "kakao_", "naver_", "deleted_");

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailVerificationService emailVerificationService;
    private final AdminAccounts adminAccounts;

    // ---------------- 회원가입 (이메일 인증 필수) ----------------

    /** 회원가입용 인증번호 발송 - 이미 가입된 이메일이면 바로 알려준다 */
    @Transactional
    public EmailCodeResponse sendRegisterCode(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("이미 가입된 이메일입니다");
        }
        EmailVerificationService.SendResult result = emailVerificationService.sendCode(email, Purpose.REGISTER);
        return new EmailCodeResponse("인증번호를 보냈습니다", result.expiresInSeconds(), result.devCode());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (isReservedUsername(request.getUsername())) {
            throw new IllegalArgumentException("사용할 수 없는 아이디입니다 (google_, kakao_, naver_, deleted_로 시작할 수 없음)");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("이미 사용 중인 아이디입니다: " + request.getUsername());
        }
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("이미 가입된 이메일입니다");
        }
        // 인증번호로 확인한 이메일과 가입 이메일이 같아야 한다 (토큰은 한 번 쓰면 사라짐)
        emailVerificationService.consume(email, Purpose.REGISTER, request.getEmailVerificationToken());

        User user = userRepository.save(User.builder()
                .username(request.getUsername())
                .nickname(request.getNickname().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName().trim())
                .email(email)
                .phone(normalizePhone(request.getPhone()))
                .birthDate(request.getBirthDate())
                .role(adminAccounts.isAdmin(request.getUsername()) ? Role.ADMIN : Role.USER)
                .build());

        userProfileRepository.save(UserProfile.builder()
                .user(user)
                .build());
        createCart(user);

        return toAuthResponse(user);
    }

    // ---------------- 로그인 ----------------

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다: " + request.getUsername()));
        return toAuthResponse(user);
    }

    /** 현재 로그인 정보 (새로고침 시 닉네임/권한을 최신으로 맞추는 용도) */
    @Transactional(readOnly = true)
    public AuthResponse me(Authentication authentication, String currentToken) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다"));
        return new AuthResponse(currentToken, user.getId(), user.getUsername(), user.getNickname(), user.getRole().name());
    }

    @Transactional
    public AuthResponse oauthLogin(String provider, String providerId, String profileNickname) {
        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> {
                    String username = provider + "_" + providerId;
                    User newUser = userRepository.save(User.builder()
                            .username(username)
                            // 소셜 프로필 이름이 있으면 닉네임으로 (없으면 google_123... 같은 아이디)
                            .nickname(profileNickname != null ? profileNickname : username)
                            // 소셜 사용자는 로컬 로그인을 쓰지 않으므로 알 수 없는 랜덤 비밀번호로 채워둔다
                            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .provider(provider)
                            .providerId(providerId)
                            .role(adminAccounts.isAdmin(username) ? Role.ADMIN : Role.USER)
                            .build());
                    userProfileRepository.save(UserProfile.builder().user(newUser).build());
                    createCart(newUser);
                    return newUser;
                });

        // 예전에 가입해서 닉네임이 아이디 그대로(google_123...)인 사용자는 프로필 이름으로 바꿔준다
        // (직접 닉네임을 바꾼 사용자는 건드리지 않음)
        if (profileNickname != null && user.getNickname().equals(user.getUsername())) {
            user.setNickname(profileNickname);
        }
        return toAuthResponse(user);
    }

    // ---------------- 아이디 / 비밀번호 찾기 ----------------

    @Transactional(readOnly = true)
    public FindUsernameResponse findUsername(FindUsernameRequest request) {
        User user = userRepository.findByNameAndEmail(request.getName().trim(), normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new ResourceNotFoundException(IDENTITY_MISMATCH_MESSAGE));
        return new FindUsernameResponse(maskUsername(user.getUsername()), user.getCreatedAt().toLocalDate());
    }

    /**
     * 비밀번호 재설정 1단계: 아이디와 이메일이 맞으면 가입 이메일로 인증번호를 보낸다.
     * 계정 존재 여부를 알려주지 않기 위해, 일치하지 않아도 같은 응답을 준다 (메일만 안 보냄).
     */
    @Transactional
    public EmailCodeResponse sendPasswordResetCode(PasswordResetCodeRequest request) {
        String email = normalizeEmail(request.getEmail());
        boolean matches = userRepository.findByUsername(request.getUsername().trim())
                .filter(u -> u.getProvider() == null && email.equals(u.getEmail()))
                .isPresent();
        String devCode = null;
        long expiresIn = 300;
        if (matches) {
            EmailVerificationService.SendResult result = emailVerificationService.sendCode(email, Purpose.PASSWORD_RESET);
            devCode = result.devCode();
            expiresIn = result.expiresInSeconds();
        }
        return new EmailCodeResponse("입력하신 정보가 맞다면 가입 이메일로 인증번호를 보냈습니다", expiresIn, devCode);
    }

    /** 비밀번호 재설정 2단계: 인증번호가 맞으면 10분짜리 1회용 재설정 토큰 발급 */
    @Transactional
    public PasswordResetVerifyResponse verifyForPasswordReset(PasswordResetVerifyRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByUsername(request.getUsername().trim())
                .filter(u -> u.getProvider() == null && email.equals(u.getEmail()))
                .orElseThrow(() -> new ResourceNotFoundException(IDENTITY_MISMATCH_MESSAGE));

        String verificationToken = emailVerificationService.verify(email, Purpose.PASSWORD_RESET, request.getCode());
        emailVerificationService.consume(email, Purpose.PASSWORD_RESET, verificationToken);

        String token = Hashing.randomToken();
        user.setPasswordResetTokenHash(Hashing.sha256(token));
        user.setPasswordResetExpiresAt(LocalDateTime.now().plus(PASSWORD_RESET_TTL));
        return new PasswordResetVerifyResponse(token, PASSWORD_RESET_TTL.toSeconds());
    }

    @Transactional
    public void resetPassword(PasswordResetConfirmRequest request) {
        User user = userRepository.findByPasswordResetTokenHash(Hashing.sha256(request.getResetToken()))
                .filter(u -> u.getPasswordResetExpiresAt() != null
                        && u.getPasswordResetExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "재설정 시간이 만료되었거나 올바르지 않은 요청입니다. 본인확인을 다시 진행해주세요"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // 비밀번호를 잊어서(또는 도용이 의심돼서) 재설정한 것이므로 다른 기기의 로그인도 모두 끊는다
        user.invalidateTokens();
        // 토큰은 한 번 쓰면 바로 폐기
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
    }

    // ---------------- 내부 ----------------

    // 장바구니는 가입할 때 미리 만든다 - 처음 장바구니를 쓸 때 만들면, 동시에 들어온 두 요청(목록 조회 + 담기)이
    // 둘 다 새로 만들려다 고유 제약(한 사람당 하나)에 걸릴 수 있다. 예전 가입자는 CartService가 처음 쓸 때 만든다
    private void createCart(User user) {
        cartRepository.save(Cart.builder().user(user).build());
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtTokenProvider.generateToken(user.getUsername(), user.currentTokenVersion());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getNickname(), user.getRole().name());
    }

    static boolean isReservedUsername(String username) {
        String lower = username.toLowerCase(Locale.ROOT);
        return RESERVED_USERNAME_PREFIXES.stream().anyMatch(lower::startsWith);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String phone) {
        return phone.replaceAll("\\D", "");
    }

    // 앞 2글자와 마지막 1글자만 보여주고 나머지는 * 처리 (4자 이하면 첫 글자만 보여줌)
    static String maskUsername(String username) {
        int head = username.length() <= 4 ? 1 : 2;
        int tail = username.length() <= 4 ? 0 : 1;
        return username.substring(0, head)
                + "*".repeat(username.length() - head - tail)
                + username.substring(username.length() - tail);
    }
}
