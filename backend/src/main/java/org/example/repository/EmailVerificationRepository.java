package org.example.repository;

import org.example.domain.EmailVerification;
import org.example.domain.EmailVerification.Purpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, Purpose purpose);

    Optional<EmailVerification> findByEmailAndPurposeAndVerifiedTokenHash(String email, Purpose purpose, String tokenHash);

    void deleteByEmailAndPurpose(String email, Purpose purpose);

    void deleteByCreatedAtBefore(LocalDateTime before);

    @Modifying
    @Query("update EmailVerification v set v.failedAttempts = v.failedAttempts + 1 where v.id = :id")
    int incrementFailedAttempts(@Param("id") Long id);
}
