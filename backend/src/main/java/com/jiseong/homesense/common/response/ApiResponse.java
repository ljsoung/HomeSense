package com.jiseong.homesense.common.response;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;

/**
 * COM-RES-01. 모든 API 응답을 감싸는 공통 포맷.
 * 성공: {success: true, data, error: null, timestamp}
 * 실패: {success: false, data: null, error: {code, message}, timestamp}
 *
 * <p>{@code pageMeta}는 목록 조회에서만 채워진다(FR-3.6) — 그 외 응답은 항상 null이고,
 * application.properties의 {@code spring.jackson.default-property-inclusion=non_null} 설정으로
 * null 필드는 직렬화 시 아예 빠지므로 목록이 아닌 응답의 JSON에는 {@code pageMeta} 키 자체가 없다.
 */
public record ApiResponse<T>(boolean success, T data, PageMeta pageMeta, ErrorResponse error, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null, Instant.now());
    }

    /** 목록 조회 전용 — data는 page.getContent(), pageMeta는 PageMeta.from(page)로 함께 채운다. */
    public static <T> ApiResponse<List<T>> success(Page<T> page) {
        return new ApiResponse<>(true, page.getContent(), PageMeta.from(page), null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, null, new ErrorResponse(code, message, null), Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, List<FieldError> fieldErrors) {
        return new ApiResponse<>(false, null, null, new ErrorResponse(code, message, fieldErrors), Instant.now());
    }

    public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {
    }

    /** COM-VAL-01 연계 — Bean Validation 실패 시 필드별 에러를 담는다. */
    public record FieldError(String field, String message) {
    }
}
