package com.jiseong.homesense.common.exception;

import java.util.List;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
 *
 * <p><b>응답에 원문 예외 메시지를 넣지 않는다(2026-10-22).</b> Spring MVC 예외의 getMessage()에는 Jackson 파싱 오류 원문,
 * 클래스·패키지명, 컨트롤러 메서드 시그니처, 입력값이 들어간다. 그래서 4xx도 예외 종류별 고정 문구를 쓴다
 * ({@link #clientError(Exception, HttpStatusCode)}). 요청 파라미터 이름만은 붙인다 — 클라이언트가 보낸 이름이라 내부 정보가
 * 아니다. 필드 검증 오류의 메시지는 유지하되, 타입 변환 실패(바인딩 실패)의 메시지는 원문 대신 고정 문구로 바꾼다.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CODE_VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String CODE_INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    private static final String CODE_SERVICE_TEMPORARILY_UNAVAILABLE = "SERVICE_TEMPORARILY_UNAVAILABLE";
    private static final String CODE_INVALID_REQUEST_BODY = "INVALID_REQUEST_BODY";
    private static final String CODE_INVALID_PARAMETER = "INVALID_PARAMETER";
    private static final String CODE_MISSING_PARAMETER = "MISSING_PARAMETER";

    /** 타입 변환에 실패한 필드(@ModelAttribute 바인딩)의 메시지 — 원문에는 대상 클래스명과 입력값이 들어 있다. */
    static final String MESSAGE_FIELD_TYPE_MISMATCH = "형식이 올바르지 않습니다";

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
                .map(fe -> new ApiResponse.FieldError(fe.getField(), fieldMessage(fe)))
                .toList();
        return ResponseEntity.status(status)
                .body(ApiResponse.error(CODE_VALIDATION_FAILED, "입력값이 유효하지 않습니다", fieldErrors));
    }

    /** 바인딩 실패(타입 변환)는 원문 대신 고정 문구, 검증 실패는 제약의 메시지(입력값을 넣지 않는다) 그대로. */
    private static String fieldMessage(FieldError fe) {
        return fe.isBindingFailure() ? MESSAGE_FIELD_TYPE_MISMATCH : fe.getDefaultMessage();
    }

    /**
     * 부모 클래스가 처리하는 모든 Spring MVC 요청 예외(잘못된 JSON, 지원하지 않는 메서드/미디어
     * 타입 등)가 최종적으로 거치는 지점. 원래의 status는 그대로 두고 본문만 ApiResponse로 바꾼다.
     * 부모가 넘겨준 헤더(405의 Allow, 415의 Accept 등)는 유지한다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            return ResponseEntity.status(status).headers(headers).body(internalErrorBody(e));
        }
        return ResponseEntity.status(status).headers(headers).body(clientError(e, status));
    }

    /**
     * 4xx 본문. {@code TypeMismatchException}(경로·쿼리 값 변환 실패)은 부모 클래스가 {@code MethodArgumentTypeMismatchException}을
     * 포함해 이 지점으로 보낸다.
     */
    static ApiResponse<Void> clientError(Exception e, HttpStatusCode status) {
        if (e instanceof HttpMessageNotReadableException) {
            return ApiResponse.error(CODE_INVALID_REQUEST_BODY, "요청 형식이 올바르지 않습니다");
        }
        if (e instanceof MethodArgumentTypeMismatchException mismatch) {
            return ApiResponse.error(CODE_INVALID_PARAMETER, "요청 값의 형식이 올바르지 않습니다: " + mismatch.getName());
        }
        if (e instanceof TypeMismatchException) {
            return ApiResponse.error(CODE_INVALID_PARAMETER, "요청 값의 형식이 올바르지 않습니다");
        }
        if (e instanceof MissingServletRequestParameterException missing) {
            return ApiResponse.error(CODE_MISSING_PARAMETER, "필수 요청 값이 없습니다: " + missing.getParameterName());
        }
        String code = statusCode(status);
        if (e instanceof HttpRequestMethodNotSupportedException) {
            return ApiResponse.error(code, "지원하지 않는 요청 방식입니다");
        }
        if (e instanceof HttpMediaTypeNotSupportedException) {
            return ApiResponse.error(code, "지원하지 않는 요청 본문 형식입니다");
        }
        if (e instanceof NoResourceFoundException || e instanceof NoHandlerFoundException) {
            return ApiResponse.error(code, "요청한 경로를 찾을 수 없습니다");
        }
        if (e instanceof MaxUploadSizeExceededException) {
            return ApiResponse.error(code, "요청 크기가 너무 큽니다");
        }
        return ApiResponse.error(code, "요청을 처리할 수 없습니다");
    }

    private static String statusCode(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.name() : String.valueOf(status.value());
    }

    /**
     * Redis 인프라 장애({@link RedisUnavailability})는 500이 아니라 503 {@code SERVICE_TEMPORARILY_UNAVAILABLE}(고정 문구)이다.
     * 보안 판단에 Redis가 필요한 곳(로그인·비밀번호 확인 잠금, 재설정 토큰·쿨다운, 컷오프 기록 등)은 Redis 없이 진행하지 않고
     * 예외를 그대로 던지므로 여기서 503이 된다 — 막힌 쪽 실패. 장애는 운영 문제라 CRITICAL 감사 로그(logBatchFailure) 대신 WARN
     * 한 줄만 남긴다(내부 메시지는 응답에 넣지 않는다). Redis가 아닌 DataAccessException은 예상치 못한 예외와 같다.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException e) {
        if (!RedisUnavailability.isRedisUnavailable(e)) {
            return handleUnexpected(e);
        }
        log.warn("COM-EXC-01 Redis 장애로 요청을 처리하지 못했다(503): {}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(CODE_SERVICE_TEMPORARILY_UNAVAILABLE,
                        "일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요."));
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
