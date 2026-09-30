package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.UserProfile;
import org.example.dto.profile.UserProfileRequest;
import org.example.dto.profile.UserProfileResponse;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.UserProfileRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public UserProfileResponse get(Long userId, Authentication authentication) {
        currentUserService.assertOwnership(authentication, userId);
        UserProfile profile = findByUserId(userId);
        return toResponse(profile);
    }

    @Transactional
    public UserProfileResponse update(Long userId, UserProfileRequest request, Authentication authentication) {
        currentUserService.assertOwnership(authentication, userId);
        UserProfile profile = findByUserId(userId);

        if (request.getAllergies() != null) {
            profile.setAllergies(new ArrayList<>(request.getAllergies()));
        }
        if (request.getDietTypes() != null) {
            profile.replaceDietTypes(new HashSet<>(request.getDietTypes()));
        }

        return toResponse(profile);
    }

    private UserProfile findByUserId(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("프로필을 찾을 수 없습니다: userId=" + userId));
    }

    private UserProfileResponse toResponse(UserProfile profile) {
        return new UserProfileResponse(
                profile.getUser().getId(),
                profile.getUser().getUsername(),
                profile.getUser().getNickname(),
                profile.getUser().getProvider(),
                profile.getUser().getEmail(),
                new ArrayList<>(profile.getAllergies()),
                profile.getEffectiveDietTypes().stream().sorted().toList()
        );
    }
}
