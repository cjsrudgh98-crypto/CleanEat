package org.example.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_provider_id", columnNames = {"provider", "provider_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    // 로그인 ID(username)와 분리된 표시용 이름 - 헤더 등에서 "OO님"으로 보여줄 때 이걸 쓴다
    @Column(nullable = false, length = 50)
    private String nickname;

    // 아래 본인확인 정보는 로컬 회원가입 사용자만 필수 - 소셜 로그인 사용자는 제공받지 않으므로 null일 수 있다
    @Column(length = 50)
    private String name;

    @Column(unique = true, length = 100)
    private String email;

    // 하이픈 없이 숫자만 저장 (예: 01012345678)
    @Column(length = 20)
    private String phone;

    private LocalDate birthDate;

    // 비밀번호 재설정 토큰은 원문 대신 SHA-256 해시만 보관한다 (DB가 유출돼도 토큰을 바로 쓸 수 없게)
    @Column(length = 64)
    private String passwordResetTokenHash;

    private LocalDateTime passwordResetExpiresAt;

    // 로그인 토큰(JWT)에 함께 담기는 번호. 비밀번호를 바꾸거나 재설정하면 1 올려서 그 전에 발급된 토큰을 모두 무효로 만든다
    // (토큰을 도둑맞았어도 비밀번호를 바꾸면 끊긴다). 컬럼 추가 전에 가입한 사용자는 null이라 0으로 취급한다
    @Builder.Default
    private Integer tokenVersion = 0;

    @Enumerated(EnumType.STRING)

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    // 소셜 로그인 사용자만 값이 있음 (로컬 회원가입 사용자는 둘 다 null)
    @Column(length = 20)
    private String provider;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // 탈퇴 시각 - 탈퇴하면 개인정보를 지우고 주문 기록(전자상거래법상 보관 의무)만 익명으로 남긴다
    private LocalDateTime deletedAt;

    public int currentTokenVersion() {
        return tokenVersion == null ? 0 : tokenVersion;
    }

    /** 이전에 발급된 로그인 토큰을 모두 무효로 만든다 */
    public void invalidateTokens() {
        tokenVersion = currentTokenVersion() + 1;
    }
}
