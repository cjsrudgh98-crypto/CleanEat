package org.example.dto.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.function.Function;

/**
 * 목록 한 페이지. 전체 개수를 세는 쿼리(count)는 하지 않고 다음 페이지가 있는지만 알려준다 ("더 보기" 방식).
 * @param page 0부터 시작
 */
public record PageResponse<T>(List<T> items, int page, int size, boolean hasNext) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** 요청 값이 이상해도(음수, 너무 큰 size) 안전한 범위로 맞춘다 */
    public static Pageable request(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), MAX_SIZE));
    }

    public static <S, T> PageResponse<T> of(Slice<S> slice, Function<S, T> mapper) {
        return new PageResponse<>(slice.getContent().stream().map(mapper).toList(),
                slice.getNumber(), slice.getSize(), slice.hasNext());
    }
}
