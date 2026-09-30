package org.example.repository;

import org.example.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<User> findByNameAndEmail(String name, String email);
    Optional<User> findByPasswordResetTokenHash(String passwordResetTokenHash);
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
}
