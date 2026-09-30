package org.example.dto.profile;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.domain.DietType;

import java.util.List;

@Getter
@AllArgsConstructor
public class UserProfileResponse {
    private Long userId;
    private String username;
    private String nickname;
    // 소셜 로그인 사용자면 "google"/"kakao"/"naver", 로컬 회원가입이면 null (프론트에서 비밀번호 변경 UI 노출 여부 판단용)
    private String provider;
    // 본인 조회용 - 소셜 로그인으로 가입해 이메일이 없으면 null
    private String email;
    private List<String> allergies;
    // 선택한 식단들 (빈 배열이면 제한 없음)
    private List<DietType> dietTypes;
}
