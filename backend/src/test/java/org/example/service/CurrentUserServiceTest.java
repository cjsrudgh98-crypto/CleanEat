package org.example.service;

import org.example.domain.User;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private UserRepository userRepository;

    private CurrentUserService currentUserService;

    @Test
    void 인증정보가_없으면_예외를_던진다() {
        currentUserService = new CurrentUserService(userRepository);

        assertThatThrownBy(() -> currentUserService.getCurrentUser(null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 인증된_사용자명으로_사용자를_조회한다() {
        currentUserService = new CurrentUserService(userRepository);
        Authentication authentication = new UsernamePasswordAuthenticationToken("cleaneat_user", "password", Collections.emptyList());
        User user = User.builder().id(1L).username("cleaneat_user").build();
        when(userRepository.findByUsername("cleaneat_user")).thenReturn(Optional.of(user));

        User result = currentUserService.getCurrentUser(authentication);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void 사용자를_찾을수_없으면_예외를_던진다() {
        currentUserService = new CurrentUserService(userRepository);
        Authentication authentication = new UsernamePasswordAuthenticationToken("unknown_user", "password", Collections.emptyList());
        when(userRepository.findByUsername("unknown_user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currentUserService.getCurrentUser(authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void 본인이_아닌_다른_사용자ID를_요청하면_예외를_던진다() {
        currentUserService = new CurrentUserService(userRepository);
        Authentication authentication = new UsernamePasswordAuthenticationToken("cleaneat_user", "password", Collections.emptyList());
        User user = User.builder().id(1L).username("cleaneat_user").build();
        when(userRepository.findByUsername("cleaneat_user")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> currentUserService.assertOwnership(authentication, 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 본인의_사용자ID면_예외없이_통과한다() {
        currentUserService = new CurrentUserService(userRepository);
        Authentication authentication = new UsernamePasswordAuthenticationToken("cleaneat_user", "password", Collections.emptyList());
        User user = User.builder().id(1L).username("cleaneat_user").build();
        when(userRepository.findByUsername("cleaneat_user")).thenReturn(Optional.of(user));

        currentUserService.assertOwnership(authentication, 1L);
    }
}
