package org.example.dto.ingredient;

import java.util.List;

/**
 * 유해성분 사전 한 항목.
 *
 * @param aliases 성분표에서 이 성분을 찾을 때 쓰는 다른 표기 (영어 이름, E-번호 등)
 * @param enabled 검사에 쓰이는지 (관리자 화면용 - 공개 목록에는 사용 중인 것만 나온다)
 */
public record IngredientResponse(Long id, String name, String riskLevel, String category, String description,
                                 List<String> aliases, boolean enabled) {
}
