package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.DietType;
import org.example.domain.UserProfile;
import org.example.repository.UserProfileRepository;
import org.example.repository.UserRepository;
import org.example.security.AppUserDetails;
import org.example.util.AllergenMatcher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 로그인한 사용자의 식단/알레르기 설정을 상품 목록·추천에서 쓰기 좋은 형태로 꺼낸다.
 * 비로그인이거나 프로필이 없으면 "제한 없음"으로 취급한다 (상품 API는 비로그인도 쓸 수 있으므로 예외를 던지지 않음).
 */
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    /**
     * @param diets 선택한 식단들 (비어 있으면 제한 없음). 상품은 이 식단을 "모두" 만족해야 맞는 것으로 본다
     */
    public record Preferences(boolean loggedIn, Set<DietType> diets, List<String> allergies, Set<String> expandedAllergens) {
        public static final Preferences NONE = new Preferences(false, Set.of(), List.of(), Set.of());

        public boolean hasDiet() {
            return !diets.isEmpty();
        }

        public boolean fitsDiet(Collection<DietType> suitableDiets) {
            return suitableDiets.containsAll(diets);
        }

        // 예: "비건·글루텐프리", 선택 안 했으면 "제한 없음"
        public String dietLabel() {
            if (diets.isEmpty()) return DietType.NONE.getLabel();
            return diets.stream().sorted().map(DietType::getLabel).collect(Collectors.joining("·"));
        }
    }

    @Transactional(readOnly = true)
    public Preferences of(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            return Preferences.NONE;
        }
        // JWT 필터가 이미 읽어 둔 회원 id를 쓰고, 없을 때(테스트용 목 사용자 등)만 아이디로 찾는다
        Long userId = AppUserDetails.userIdOf(authentication);
        Optional<UserProfile> found = userId != null
                ? userProfileRepository.findByUserId(userId)
                : userRepository.findByUsername(authentication.getName())
                        .flatMap(user -> userProfileRepository.findByUserId(user.getId()));
        return found
                .map(profile -> {
                    List<String> allergies = List.copyOf(profile.getAllergies());
                    return new Preferences(true, profile.getEffectiveDietTypes(), allergies, AllergenMatcher.expand(allergies));
                })
                .orElse(new Preferences(true, Set.of(), List.of(), Set.of()));
    }
}
