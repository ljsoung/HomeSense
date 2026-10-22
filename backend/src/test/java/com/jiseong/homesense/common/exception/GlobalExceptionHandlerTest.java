package com.jiseong.homesense.common.exception;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.jiseong.homesense.common.response.ApiResponse;
import com.jiseong.homesense.trade.entity.DealCategory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.common.security.AccessTokenEpochService;
import com.jiseong.homesense.common.security.UserStatusResolver;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    /*
     * JwtAuthenticationFilter는 Filter 빈이라 @WebMvcTest의 controllers 필터와 무관하게 항상
     * 컨텍스트에 올라간다. addFilters=false는 MockMvc 체인 적용만 막을 뿐 빈 생성 자체는 막지
     * 않으므로, 그 생성자 의존성인 JwtTokenProvider를 목으로 채워 컨텍스트 로딩을 통과시킨다.
     */
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private UserStatusResolver userStatusResolver;
    @MockitoBean
    private AccessTokenEpochService accessTokenEpochService;

    /*
     * GlobalExceptionHandler는 @RestControllerAdvice라 @WebMvcTest의 controllers 필터와 무관하게
     * 항상 컨텍스트에 올라간다(COM-LOG-01). 그 생성자 의존성인 AuditLogger를 목으로 채운다.
     */
    @MockitoBean
    private AuditLogger auditLogger;

    @Test
    void BusinessException은_보유한_HttpStatus와_errorCode로_응답한다() throws Exception {
        mockMvc.perform(get("/test/business-exception"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("TEST_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("존재하지 않는 테스트 리소스"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void Bean_Validation_실패는_400과_필드별_에러_목록으로_응답한다() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.error.fieldErrors[0].message").exists());
    }

    @Test
    void 예상치_못한_예외는_500이고_상세_메시지를_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value(not(containsString("boom"))));
    }

    @Test
    void 예상치_못한_예외는_COM_LOG_01_AuditLogger로_CRITICAL_기록을_남긴다() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError());

        verify(auditLogger).logBatchFailure(eq("COM-EXC-01"), any(IllegalStateException.class));
    }

    /*
     * GlobalExceptionHandler가 ResponseEntityExceptionHandler를 상속하지 않으면 이 예외도
     * java.lang.Exception이라 handleUnexpected가 가로채 500으로 응답이 바뀐다 — 회귀 테스트.
     */
    @Test
    void 지원하지_않는_HTTP_메서드는_500이_아니라_405로_응답한다() throws Exception {
        mockMvc.perform(post("/test/business-exception"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 형식이_잘못된_JSON_요청은_500이_아니라_400으로_응답한다() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void Redis_연결_실패는_503_SERVICE_TEMPORARILY_UNAVAILABLE이고_내부_메시지를_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/test/redis-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SERVICE_TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value(not(containsString("6379"))));

        verify(auditLogger, never()).logBatchFailure(any(), any());
    }

    @Test
    void Redis_명령_타임아웃도_503이다() throws Exception {
        mockMvc.perform(get("/test/redis-timeout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SERVICE_TEMPORARILY_UNAVAILABLE"));
    }

    @Test
    void Redis가_아닌_DataAccessException은_그대로_500이다() throws Exception {
        mockMvc.perform(get("/test/db-timeout"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"));

        verify(auditLogger).logBatchFailure(eq("COM-EXC-01"), any(QueryTimeoutException.class));
    }

    // ---- 오류 응답에 내부 정보를 노출하지 않는다(2026-10-22) ----

    /** 응답 본문에 클래스·패키지명, 원문 예외 메시지, Jackson, 입력값이 없어야 한다. */
    private static ResultMatcher noInternalDetails(String... inputs) {
        return result -> {
            String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(body).doesNotContain("com.", "org.", "java.", "Exception", "exception", "Jackson", "JSON parse",
                    "Failed to convert", "Required request");
            for (String input : inputs) {
                assertThat(body).doesNotContain(input);
            }
        };
    }

    private ResultActions expectError(ResultActions actions, String code, String message) throws Exception {
        return actions.andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").value(message));
    }

    @Test
    void 잘못된_JSON은_INVALID_REQUEST_BODY_고정_문구다() throws Exception {
        expectError(mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest()), "INVALID_REQUEST_BODY", "요청 형식이 올바르지 않습니다")
                .andExpect(noInternalDetails("bad"));
    }

    @Test
    void 필드_타입이_맞지_않는_JSON도_INVALID_REQUEST_BODY다() throws Exception {
        expectError(mockMvc.perform(post("/test/typed").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flag\":\"secret-xyz\"}"))
                .andExpect(status().isBadRequest()), "INVALID_REQUEST_BODY", "요청 형식이 올바르지 않습니다")
                .andExpect(noInternalDetails("secret-xyz", "Boolean"));
    }

    @Test
    void 본문이_없으면_컨트롤러_시그니처를_노출하지_않고_INVALID_REQUEST_BODY다() throws Exception {
        expectError(mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()), "INVALID_REQUEST_BODY", "요청 형식이 올바르지 않습니다")
                .andExpect(noInternalDetails("TestController", "TestRequest"));
    }

    @Test
    void 경로_변수_타입_불일치는_파라미터_이름만_붙인_INVALID_PARAMETER다() throws Exception {
        expectError(mockMvc.perform(get("/test/items/secret-xyz")).andExpect(status().isBadRequest()),
                "INVALID_PARAMETER", "요청 값의 형식이 올바르지 않습니다: id")
                .andExpect(noInternalDetails("secret-xyz", "Long"));
    }

    @Test
    void 쿼리_파라미터_타입_불일치는_INVALID_PARAMETER다() throws Exception {
        expectError(mockMvc.perform(get("/test/query").param("count", "secret-xyz")).andExpect(status().isBadRequest()),
                "INVALID_PARAMETER", "요청 값의 형식이 올바르지 않습니다: count")
                .andExpect(noInternalDetails("secret-xyz"));
    }

    @Test
    void 필수_파라미터_누락은_MISSING_PARAMETER다() throws Exception {
        expectError(mockMvc.perform(get("/test/query")).andExpect(status().isBadRequest()),
                "MISSING_PARAMETER", "필수 요청 값이 없습니다: count")
                .andExpect(noInternalDetails());
    }

    @Test
    void ModelAttribute_바인딩_실패는_필드는_남기고_메시지는_고정_문구다() throws Exception {
        mockMvc.perform(get("/test/filter").param("size", "secret-xyz").param("category", "SECRET_ENUM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.error.fieldErrors[0].message").value(GlobalExceptionHandler.MESSAGE_FIELD_TYPE_MISMATCH))
                .andExpect(jsonPath("$.error.fieldErrors[1].message").value(GlobalExceptionHandler.MESSAGE_FIELD_TYPE_MISMATCH))
                .andExpect(noInternalDetails("secret-xyz", "SECRET_ENUM", "DealCategory", "Integer"));
    }

    @Test
    void 검증_실패_메시지에_거부된_입력값이_들어가지_않는다() throws Exception {
        mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ok\",\"memo\":\"secret-input-0123456789\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("memo"))
                .andExpect(noInternalDetails("secret-input-0123456789"));
    }

    @Test
    void 지원하지_않는_메서드는_405_고정_문구이고_Allow_헤더를_유지한다() throws Exception {
        expectError(mockMvc.perform(delete("/test/business-exception")).andExpect(status().isMethodNotAllowed()),
                "METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다")
                .andExpect(result -> assertThat(result.getResponse().getHeader("Allow")).contains("GET"))
                .andExpect(noInternalDetails("DELETE"));
    }

    @Test
    void 지원하지_않는_Content_Type은_415_고정_문구다() throws Exception {
        expectError(mockMvc.perform(post("/test/validate").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType()), "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 본문 형식입니다")
                .andExpect(noInternalDetails("text/plain"));
    }

    @Test
    void 없는_경로는_500이_아니라_404_고정_문구다() throws Exception {
        expectError(mockMvc.perform(get("/test/no-such-path")).andExpect(status().isNotFound()),
                "NOT_FOUND", "요청한 경로를 찾을 수 없습니다")
                .andExpect(noInternalDetails("no-such-path", "static resource"));
    }

    @Test
    void 업로드_크기_초과는_413_고정_문구다() {
        ApiResponse<Void> body = GlobalExceptionHandler.clientError(new MaxUploadSizeExceededException(1024),
                HttpStatus.PAYLOAD_TOO_LARGE);

        assertThat(body.error().code()).isEqualTo("CONTENT_TOO_LARGE");
        assertThat(body.error().message()).isEqualTo("요청 크기가 너무 큽니다");
    }

    @RestController
    static class TestController {

        @GetMapping("/test/items/{id}")
        void item(@PathVariable Long id) {
        }

        @GetMapping("/test/query")
        void query(@RequestParam int count) {
        }

        @GetMapping("/test/filter")
        void filter(@ModelAttribute TestFilter filter) {
        }

        @PostMapping("/test/typed")
        void typed(@RequestBody TypedRequest request) {
        }

        @GetMapping("/test/redis-down")
        void throwRedisDown() {
            throw new RedisConnectionFailureException("Unable to connect to localhost:6379");
        }

        @GetMapping("/test/redis-timeout")
        void throwRedisTimeout() {
            throw new QueryTimeoutException("Redis command timed out",
                    new io.lettuce.core.RedisCommandTimeoutException("Command timed out after 1 second(s)"));
        }

        @GetMapping("/test/db-timeout")
        void throwDbTimeout() {
            // JDBC 쿼리 타임아웃 — 원인이 Lettuce가 아니라 Redis 장애가 아니다.
            throw new QueryTimeoutException("Statement cancelled due to timeout", new java.sql.SQLTimeoutException());
        }

        @GetMapping("/test/business-exception")
        void throwBusiness() {
            throw new BusinessException("TEST_NOT_FOUND", "존재하지 않는 테스트 리소스", HttpStatus.NOT_FOUND);
        }

        @PostMapping("/test/validate")
        void throwValidation(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/unexpected")
        void throwUnexpected() {
            throw new IllegalStateException("boom");
        }
    }

    record TestRequest(@NotBlank String name, @Size(max = 5) String memo) {
    }

    record TypedRequest(Boolean flag) {
    }

    record TestFilter(Integer size, DealCategory category) {
    }
}
