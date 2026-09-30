package org.example.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 이메일 인증번호 1건. 인증번호/인증완료 토큰은 원문 대신 SHA-256 해시만 저장한다.
 *  1) 인증번호 발송 -> codeHash, expiresAt
 *  2) 인증번호 확인 -> verifiedTokenHash 발급 (가입/비밀번호 재설정 요청에 함께 보내야 함, 이메일 변경은 바로 소비)
 *  3) 가입/재설정에 쓰이면 삭제 (1회용)
 */
@Entity
@Table(name = "email_verifications", indexes = @Index(name = "idx_email_verifications_email", columnList = "email, purpose"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerification {

    public enum Purpose { REGISTER, PASSWORD_RESET, EMAIL_CHANGE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    @Column(nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // 틀린 횟수 - 5번 틀리면 이 인증번호는 못 쓴다 (6자리 무작위 대입 방지)
    @Builder.Default
    private int failedAttempts = 0;

    @Column(length = 64)
    private String verifiedTokenHash;

    private LocalDateTime verifiedTokenExpiresAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
