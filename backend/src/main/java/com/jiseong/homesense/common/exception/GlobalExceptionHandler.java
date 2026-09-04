package com.jiseong.homesense.common.exception;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * COM-EXC-01. 모든 Controller의 예외를 가로채 COM-RES-01 표준 에러 포맷으로 변환하는 단일 지점.
 * 3~4장의 모든 도메인 예외는 BusinessException을 상속하므로 도메인별 @ExceptionHandler를
 * 추가하지 않고 이 클래스 하나가 전부 처리한다.
 *
 * <p>ResponseEntityExceptionHandler를 상속한다 — 잘못된 JSON, 지원하지 않는 HTTP 메서드/미디어
 * 타입 등 Spring MVC 자체가 던지는 요청 처리 예외도 전부 java.lang.Exception이라 상속하지 않으면
 * 아래 handleUnexpected(Exception)이 먼저 가로채 원래 4xx여야 할 응답이 500으로 바뀐다. 이 부모
 * 클래스가 그 예외들을 구체 타입으로 이미 처리하므로(ExceptionHandlerMethodResolver가 더 구체적인
 * 핸들러를 우선 선택), handleExceptionInternal()만 오버라이드해 본문을 COM-RES-01 포맷으로
 * 바꾸면 상태 코드는 그대로 유지된다.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CODE_VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String CODE_INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    private final AuditLogger auditLogger;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(e.httpStatus())
                .body(ApiResponse.error(e.errorCode(), e.getMessage()));
    }

    /** COM-VAL-01 연계 — 필드별 에러 목록으로 변환해 프론트가 인라인 에러로 표시할 수 있게 한다. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(status)
                .body(ApiResponse.error(CODE_VALIDATION_FAILED, "입력값이 유효하지 않습니다", fieldErrors));
    }

    /**
     * 부모 클래스가 처리하는 모든 Spring MVC 요청 예외(잘못된 JSON, 지원하지 않는 메서드/미디어
     * 타입 등)가 최종적으로 거치는 지점. 원래의 status는 그대로 두고 본문만 ApiResponse로 바꾼다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            return ResponseEntity.status(status).body(internalErrorBody(e));
        }
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String code = resolved != null ? resolved.name() : String.valueOf(status.value());
        String message = e.getMessage() != null ? e.getMessage() : code;
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }

    /** 예상치 못한 예외 — 상세 메시지는 노출하지 않고 스택트레이스만 남긴다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(internalErrorBody(e));
    }

    private ApiResponse<Void> internalErrorBody(Exception e) {
        auditLogger.logBatchFailure("COM-EXC-01", e);
        return ApiResponse.error(CODE_INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }
}
