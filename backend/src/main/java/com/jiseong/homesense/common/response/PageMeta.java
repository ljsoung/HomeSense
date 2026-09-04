package com.jiseong.homesense.common.response;

import org.springframework.data.domain.Page;

/**
 * COM-RES-01. 목록 조회 응답에서 {@code data}와 함께 담기는 페이지네이션 메타데이터(FR-3.6).
 * page는 Spring Data {@link Page}가 쓰는 0-base를 그대로 노출한다 — Controller가 요청 파라미터를
 * {@link org.springframework.data.domain.Pageable}로 바꿀 때 0-base로 맞추면 응답까지 일관된다.
 */
public record PageMeta(int page, int size, long totalElements, int totalPages) {

    public static PageMeta from(Page<?> page) {
        return new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
