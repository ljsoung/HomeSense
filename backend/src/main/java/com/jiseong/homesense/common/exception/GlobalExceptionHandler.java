package com.jiseong.homesense.common.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.jiseong.homesense.common.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * COM-EXC-01. 모든 Controller의 예외를 가로채 COM-RES-01 표준 에러 포맷으로 변환하는 단일 지점.
 * 3~4장의 모든 도메인 예외는 BusinessException을 상속하므로 도메인별 @ExceptionHandler를
 * 추가하지 않고 이 클래스 하나가 전부 처리한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String CODE_VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String CODE_INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(e.httpStatus())
                .body(ApiResponse.error(e.errorCode(), e.getMessage()));
    }

    /** COM-VAL-01 연계 — 필드별 에러 목록으로 변환해 프론트가 인라인 에러로 표시할 수 있게 한다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        List<ApiResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(CODE_VALIDATION_FAILED, "입력값이 유효하지 않습니다", fieldErrors));
    }

    /** 예상치 못한 예외 — 상세 메시지는 노출하지 않고 스택트레이스만 남긴다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("COM-EXC-01 예상치 못한 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(CODE_INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
    }
}
