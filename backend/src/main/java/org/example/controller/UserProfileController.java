package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.profile.UserProfileRequest;
import org.example.dto.profile.UserProfileResponse;
import org.example.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{id}/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable("id") Long userId,
                                                            Authentication authentication) {
        return ResponseEntity.ok(userProfileService.get(userId, authentication));
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(@PathVariable("id") Long userId,
                                                               @Valid @RequestBody UserProfileRequest request,
                                                               Authentication authentication) {
        return ResponseEntity.ok(userProfileService.update(userId, request, authentication));
    }
}
