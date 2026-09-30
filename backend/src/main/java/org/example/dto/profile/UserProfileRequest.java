package org.example.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.example.domain.DietType;

import java.util.List;

@Getter
@Setter
public class UserProfileRequest {
    @Size(max = 30, message = "알레르기는 30개까지 등록할 수 있습니다")
    private List<@NotBlank(message = "알레르기 이름이 비어 있습니다")
                 @Size(max = 30, message = "알레르기 이름은 30자 이하여야 합니다") String> allergies;
    // 여러 개 선택 가능. 빈 배열이면 "제한 없음"
    private List<@NotNull(message = "식단 종류가 비어 있습니다") DietType> dietTypes;
}
