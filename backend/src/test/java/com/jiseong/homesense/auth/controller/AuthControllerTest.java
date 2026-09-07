package com.jiseong.homesense.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jiseong.homesense.auth.dto.EmailCheckResponse;
import com.jiseong.homesense.auth.dto.LoginCommand;
import com.jiseong.homesense.auth.dto.LoginResponse;
import com.jiseong.homesense.auth.dto.SignupCommand;
import com.jiseong.homesense.auth.dto.SignupResponse;
import com.jiseong.homesense.auth.dto.TokenResponse;
import com.jiseong.homesense.auth.service.AuthService;
import com.jiseong.homesense.common.exception.InvalidCredentialsException;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.common.security.UserPrincipal;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    /*
     * JwtAuthenticationFilter/GlobalExceptionHandler는 controllers 필터와 무관하게 항상 컨텍스트에
     * 올라간다 — 다른 @WebMvcTest들과 동일한 이유(GlobalExceptionHandlerTest 참고).
     */
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private AuditLogger auditLogger;

    /*
     * addFilters=false라 JwtAuthenticationFilter/SecurityContextHolderFilter가 돌지 않는다 —
     * SecurityMockMvcRequestPostProcessors.authentication()은 세션에만 SecurityContext를 저장하고
     * 그걸 SecurityContextHolder로 옮기는 건 필터 몫이라 addFilters=false에선 무의미하다
     * (JwtAuthenticationFilterTest와 동일하게 SecurityContextHolder를 직접 채우고 정리한다).
     */
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 회원가입_성공하면_200과_토큰_및_요약정보를_반환한다() throws Exception {
        when(authService.signup(new SignupCommand("new@test.com", "Abcd1234!", "닉네임")))
                .thenReturn(new SignupResponse("access-token", "refresh-token", 1800L, 1L, "new@test.com", "닉네임"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@test.com\",\"password\":\"Abcd1234!\",\"nickname\":\"닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.email").value("new@test.com"));
    }

    @Test
    void 회원가입_이메일_형식이_잘못되면_400과_필드에러를_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"Abcd1234!\",\"nickname\":\"닉네임\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("email"));
    }

    @Test
    void 회원가입_이메일이_100자를_넘으면_400과_필드에러를_반환한다() throws Exception {
        // user.email이 VARCHAR(100)이라 형식은 유효해도 컬럼 길이를 넘으면 INSERT 시점 truncation
        // 오류로 500이 새는 걸 막기 위한 회귀 테스트(코드리뷰에서 지적됨).
        String tooLongEmail = "a".repeat(92) + "@test.com";
        assertThat(tooLongEmail).hasSizeGreaterThan(100);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + tooLongEmail + "\",\"password\":\"Abcd1234!\",\"nickname\":\"닉네임\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("email"));
    }

    @Test
    void 로그인_성공하면_200과_토큰을_반환한다() throws Exception {
        when(authService.login(new LoginCommand("user@test.com", "Abcd1234!")))
                .thenReturn(new LoginResponse("access-token", "refresh-token", 1800L));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@test.com\",\"password\":\"Abcd1234!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    void 로그인_실패하면_AuthService의_예외가_401로_변환된다() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@test.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 토큰_재발급이_성공하면_200과_새_Access_Token을_반환한다() throws Exception {
        when(authService.refreshAccessToken("refresh-token")).thenReturn(new TokenResponse("new-access-token", 1800L));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
    }

    @Test
    void 로그아웃하면_인증된_사용자ID로_서비스를_호출한다() throws Exception {
        var principal = new UserPrincipal(1L, "USER");
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).logout(eq(1L), eq("refresh-token"));
    }

    @Test
    void 이메일_중복확인_결과를_반환한다() throws Exception {
        when(authService.isEmailDuplicate("dup@test.com")).thenReturn(true);

        mockMvc.perform(get("/api/auth/check-email").param("email", "dup@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicate").value(true));
    }
}
