package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.User;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("인증이 필요합니다");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다: " + authentication.getName()));
    }

    public void assertOwnership(Authentication authentication, Long requestedUserId) {
        User current = getCurrentUser(authentication);
        if (!current.getId().equals(requestedUserId)) {
            throw new AccessDeniedException("본인의 정보만 조회/수정할 수 있습니다");
        }
    }
}
