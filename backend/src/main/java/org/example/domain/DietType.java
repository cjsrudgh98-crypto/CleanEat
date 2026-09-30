package org.example.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// DB에는 상수 이름이 저장되므로 기존 상수 이름은 바꾸지 말 것 - 라벨만 바꾸는 건 괜찮다
@Getter
@RequiredArgsConstructor
public enum DietType {
    NONE("제한 없음"),
    VEGAN("비건"),
    VEGETARIAN("베지테리언"),
    KETO("케토"),
    GLUTEN_FREE("글루텐프리"),
    LOW_SODIUM("저나트륨");

    private final String label;
}
