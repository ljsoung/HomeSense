package com.jiseong.homesense.common.response;

import java.time.Instant;
import java.util.List;

/**
 * COM-RES-01. 모든 API 응답을 감싸는 공통 포맷.
 * 성공: {success: true, data, error: null, timestamp}
 * 실패: {success: false, data: null, error: {code, message}, timestamp}
 */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorResponse(code, message, null), Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, List<FieldError> fieldErrors) {
        return new ApiResponse<>(false, null, new ErrorResponse(code, message, fieldErrors), Instant.now());
    }

    public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {
    }

    /** COM-VAL-01 연계 — Bean Validation 실패 시 필드별 에러를 담는다. */
    public record FieldError(String field, String message) {
    }
}
