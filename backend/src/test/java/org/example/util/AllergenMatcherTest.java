package org.example.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AllergenMatcherTest {

    @Test
    void 견과류로_등록하면_아몬드_호두_잣도_걸러진다() {
        Set<String> expanded = AllergenMatcher.expand(List.of("견과류"));

        assertThat(AllergenMatcher.matches(List.of("아몬드", "대두"), expanded)).containsExactly("아몬드");
        assertThat(AllergenMatcher.matches(List.of("호두"), expanded)).containsExactly("호두");
        assertThat(AllergenMatcher.matches(List.of("잣"), expanded)).containsExactly("잣");
    }

    @Test
    void 다른_표현도_같은_알레르기로_인식한다() {
        Set<String> expanded = AllergenMatcher.expand(List.of("유제품", "달걀", " 콩 "));

        assertThat(AllergenMatcher.matches(List.of("우유", "계란", "대두", "밀"), expanded))
                .containsExactly("계란", "대두", "우유");
    }

    @Test
    void 해당_없는_상품이나_알레르기_미등록이면_빈_목록이다() {
        assertThat(AllergenMatcher.matches(List.of("밀"), AllergenMatcher.expand(List.of("우유")))).isEmpty();
        assertThat(AllergenMatcher.matches(List.of("우유"), AllergenMatcher.expand(List.of()))).isEmpty();
        assertThat(AllergenMatcher.expand(null)).isEmpty();
    }
}
