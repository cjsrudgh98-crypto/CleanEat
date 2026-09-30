package org.example.exception;

import lombok.Getter;

import java.util.List;

/**
 * 내 알레르기 성분이 든 상품을 장바구니에 처음 담으려 할 때. 화면에서 확인을 받은 뒤
 * allergyConfirmed=true로 다시 요청해야 담긴다 (409 + code ALLERGY_CONFIRM_REQUIRED).
 */
@Getter
public class AllergyConfirmationRequiredException extends RuntimeException {

    public static final String CODE = "ALLERGY_CONFIRM_REQUIRED";

    private final Long productId;
    private final String productName;
    private final List<String> allergens;

    public AllergyConfirmationRequiredException(Long productId, String productName, List<String> allergens) {
        super(productName + "에 회원님의 알레르기 성분(" + String.join(", ", allergens) + ")이 들어 있어요");
        this.productId = productId;
        this.productName = productName;
        this.allergens = List.copyOf(allergens);
    }
}
