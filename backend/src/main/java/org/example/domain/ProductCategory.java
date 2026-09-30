package org.example.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 선언 순서가 곧 상품 목록 화면의 카테고리 표시 순서다.
// DB에는 상수 이름(EnumType.STRING)이 저장되므로 기존 상수 이름은 바꾸지 말 것 - 라벨만 바꾸는 건 괜찮다.
@Getter
@RequiredArgsConstructor
public enum ProductCategory {
    SNACK("과자·스낵"),
    NUTS("견과·건과일"),
    BAKERY("베이커리·시리얼"),
    MEAL("간편식"),
    GRAIN("쌀·잡곡·면"),
    DAIRY("유제품·두유"),
    BEVERAGE("음료·차"),
    PROTEIN("단백질·건강식"),
    SAUCE("소스·양념"),
    ETC("기타");

    private final String label;
}
