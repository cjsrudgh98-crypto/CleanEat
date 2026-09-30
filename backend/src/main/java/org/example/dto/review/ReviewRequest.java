package org.example.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 리뷰 작성/수정. productId는 작성할 때만 쓰인다 (수정 시 무시) */
@Getter
@Setter
public class ReviewRequest {

    private Long productId;

    @NotNull(message = "별점을 선택해주세요")
    @Min(value = 1, message = "별점은 1~5점입니다")
    @Max(value = 5, message = "별점은 1~5점입니다")
    private Integer rating;

    // 비워도 된다 (별점만 남기기)
    @Size(max = 500, message = "리뷰는 500자 이하로 써주세요")
    private String content;
}
