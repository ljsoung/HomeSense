package com.jiseong.homesense.common.validation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.JwtTokenProvider;

import jakarta.validation.Valid;

/**
 * COM-VAL-01이 COM-EXC-01과 실제로 연계되는지(요청 DTO의 @ValidPassword/@ValidNickname 위반 →
 * MethodArgumentNotValidException → GlobalExceptionHandler의 400 + 필드별 에러 응답, 설계서
 * "동작 방식" 2번) 끝까지 검증한다. 각 제약의 판정 로직 자체는 PasswordValidatorTest/
 * NicknameValidatorTest가 담당하므로 여기서는 재검증하지 않는다.
 *
 * <p>GlobalExceptionHandler는 별도 소비자를 위한 "ValidationExceptionHandler" 클래스를 새로 만들지
 * 않고 그대로 재사용한다 — COM-EXC-01 원칙("도메인별 @ExceptionHandler를 추가하지 말고 전역
 * @RestControllerAdvice 하나가 전부 처리")과 설계서가 충돌해, 이미 굳어진 COM-EXC-01 쪽을 따랐다.
 */
@WebMvcTest(controllers = SignupPolicyValidationIntegrationTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SignupPolicyValidationIntegrationTest.TestController.class)
class SignupPolicyValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /*
     * GlobalExceptionHandlerTest와 동일한 이유 — JwtAuthenticationFilter(JwtTokenProvider 의존)와
     * GlobalExceptionHandler(AuditLogger 의존)가 @WebMvcTest의 controllers 필터와 무관하게 항상
     * 컨텍스트에 올라간다.
     */
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AuditLogger auditLogger;

    @Test
    void 비밀번호_정책을_위반하면_400과_password_필드_에러로_응답한다() throws Exception {
        // 닉네임은 유효하게 둬서 필드 에러가 password 하나만 나오게 한다.
        mockMvc.perform(post("/test/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"abcdefgh\",\"nickname\":\"닉네임\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.error.fieldErrors[0].message").exists());
    }

    @Test
    void 닉네임_정책을_위반하면_400과_nickname_필드_에러로_응답한다() throws Exception {
        // 비밀번호는 유효하게 둬서 필드 에러가 nickname 하나만 나오게 한다.
        mockMvc.perform(post("/test/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Abcd1234!\",\"nickname\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
    }

    @Test
    void 비밀번호와_닉네임_모두_정책을_충족하면_통과한다() throws Exception {
        mockMvc.perform(post("/test/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Abcd1234!\",\"nickname\":\"닉네임\"}"))
                .andExpect(status().isOk());
    }

    @RestController
    static class TestController {

        @PostMapping("/test/signup")
        void signup(@Valid @RequestBody SignupTestRequest request) {
        }
    }

    record SignupTestRequest(@ValidPassword String password, @ValidNickname String nickname) {
    }
}
