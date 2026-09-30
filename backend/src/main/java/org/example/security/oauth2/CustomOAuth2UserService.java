package org.example.security.oauth2;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Google/Kakao/Naver의 서로 다른 사용자정보 응답 형태를 provider별 고유 ID(providerId)로 정규화한다.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String providerId = extractProviderId(registrationId, attributes);
        String nameAttributeKey = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        return new OAuth2UserPrincipal(registrationId, providerId, attributes, nameAttributeKey,
                extractNickname(registrationId, attributes));
    }

    // google: name / kakao: properties.nickname 또는 kakao_account.profile.nickname / naver: response.nickname 또는 name
    @SuppressWarnings("unchecked")
    static String extractNickname(String registrationId, Map<String, Object> attributes) {
        Object value = switch (registrationId) {
            case "google" -> attributes.get("name");
            case "kakao" -> {
                Object properties = attributes.get("properties");
                Object nickname = properties instanceof Map<?, ?> p ? p.get("nickname") : null;
                if (nickname == null && attributes.get("kakao_account") instanceof Map<?, ?> account
                        && account.get("profile") instanceof Map<?, ?> profile) {
                    nickname = profile.get("nickname");
                }
                yield nickname;
            }
            case "naver" -> {
                Object response = attributes.get("response");
                if (!(response instanceof Map<?, ?> r)) yield null;
                yield r.get("nickname") != null ? r.get("nickname") : r.get("name");
            }
            default -> null;
        };
        if (value == null || String.valueOf(value).isBlank()) return null;
        String nickname = String.valueOf(value).trim();
        return nickname.length() > 20 ? nickname.substring(0, 20) : nickname;
    }

    private String extractProviderId(String registrationId, Map<String, Object> attributes) {
        switch (registrationId) {
            case "google":
                return String.valueOf(attributes.get("sub"));
            case "kakao":
                return String.valueOf(attributes.get("id"));
            case "naver": {
                Object response = attributes.get("response");
                if (response instanceof Map<?, ?> responseMap && responseMap.get("id") != null) {
                    return String.valueOf(responseMap.get("id"));
                }
                throw new OAuth2AuthenticationException("네이버 사용자 정보 형식이 올바르지 않습니다");
            }
            default:
                throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인입니다: " + registrationId);
        }
    }
}
