package com.jiseong.homesense.common.exception;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.JwtTokenProvider;

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

    @RestController
    static class TestController {

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

    record TestRequest(@NotBlank String name) {
    }
}
