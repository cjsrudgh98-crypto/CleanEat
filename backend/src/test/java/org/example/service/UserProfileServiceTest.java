package org.example.service;

import org.example.domain.DietType;
import org.example.domain.User;
import org.example.domain.UserProfile;
import org.example.dto.profile.UserProfileRequest;
import org.example.dto.profile.UserProfileResponse;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private CurrentUserService currentUserService;

    private UserProfileService userProfileService;

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("cleaneat_user", "password", Collections.emptyList());

    @Test
    void 프로필을_조회한다() {
        userProfileService = new UserProfileService(userProfileRepository, currentUserService);
        User user = User.builder().id(1L).username("cleaneat_user").build();
        UserProfile profile = UserProfile.builder()
                .id(1L).user(user).allergies(List.of("밀", "계란")).dietType(DietType.VEGAN).build();
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        UserProfileResponse response = userProfileService.get(1L, authentication);

        assertThat(response.getUsername()).isEqualTo("cleaneat_user");
        assertThat(response.getAllergies()).containsExactly("밀", "계란");
        // 예전(식단 1개) 컬럼에 저장된 값도 이어서 보여준다
        assertThat(response.getDietTypes()).containsExactly(DietType.VEGAN);
    }

    @Test
    void 프로필이_없으면_예외를_던진다() {
        userProfileService = new UserProfileService(userProfileRepository, currentUserService);
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.get(1L, authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 프로필을_수정하면_알레르기와_식이유형이_갱신된다() {
        userProfileService = new UserProfileService(userProfileRepository, currentUserService);
        User user = User.builder().id(1L).username("cleaneat_user").build();
        UserProfile profile = UserProfile.builder()
                .id(1L).user(user).allergies(List.of("밀")).dietType(DietType.NONE).build();
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        UserProfileRequest request = new UserProfileRequest();
        request.setAllergies(List.of("갑각류", "땅콩"));
        request.setDietTypes(List.of(DietType.KETO, DietType.GLUTEN_FREE));

        UserProfileResponse response = userProfileService.update(1L, request, authentication);

        assertThat(response.getAllergies()).containsExactly("갑각류", "땅콩");
        assertThat(response.getDietTypes()).containsExactly(DietType.KETO, DietType.GLUTEN_FREE);
    }

    @Test
    void 식단을_모두_해제하면_제한없음이_되고_예전_값도_남지_않는다() {
        userProfileService = new UserProfileService(userProfileRepository, currentUserService);
        User user = User.builder().id(1L).username("cleaneat_user").build();
        UserProfile profile = UserProfile.builder()
                .id(1L).user(user).allergies(List.of()).dietType(DietType.VEGAN).build();
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        UserProfileRequest request = new UserProfileRequest();
        request.setDietTypes(List.of());

        UserProfileResponse response = userProfileService.update(1L, request, authentication);

        assertThat(response.getDietTypes()).isEmpty();
    }

    @Test
    void 소유자가_아니면_조회시_예외를_던진다() {
        userProfileService = new UserProfileService(userProfileRepository, currentUserService);
        org.springframework.security.access.AccessDeniedException denied =
                new org.springframework.security.access.AccessDeniedException("본인의 정보만 조회/수정할 수 있습니다");
        org.mockito.Mockito.doThrow(denied).when(currentUserService).assertOwnership(authentication, 2L);

        assertThatThrownBy(() -> userProfileService.get(2L, authentication))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
