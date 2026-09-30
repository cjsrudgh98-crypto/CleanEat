package org.example.security.oauth2;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Getter
public class OAuth2UserPrincipal implements OAuth2User {

    private final String provider;
    private final String providerId;
    private final Map<String, Object> attributes;
    private final String nameAttributeKey;
    // 소셜 서비스 프로필의 이름/닉네임 (없으면 null) - 처음 가입할 때 CleanEat 닉네임으로 쓴다
    private final String profileNickname;

    public OAuth2UserPrincipal(String provider, String providerId, Map<String, Object> attributes, String nameAttributeKey,
                               String profileNickname) {
        this.provider = provider;
        this.providerId = providerId;
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
        this.profileNickname = profileNickname;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getName() {
        return String.valueOf(attributes.get(nameAttributeKey));
    }
}
