package com.jiseong.homesense.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jiseong.homesense.auth.dto.LoginResponse;
import com.jiseong.homesense.auth.service.AuthService;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.common.security.UserStatusResolver;
import com.jiseong.homesense.common.security.RestAuthenticationEntryPoint;
import com.jiseong.homesense.common.security.SecurityConfig;

/**
 * 실제 {@link SecurityConfig} 필터 체인을 태워(다른 컨트롤러 슬라이스 테스트는 {@code addFilters=false}라 이 규칙을
 * 검증하지 못한다) 탈퇴 철회가 인증 전 요청으로 열려 있는지 확인한다. 탈퇴한 계정은 로그인할 수 없어 Access Token이
 * 있을 수 없으므로, {@code /api/auth/reactivate}가 login/signup처럼 {@code anyRequest().permitAll()}에 걸려야
 * 한다 — 실수로 인증 필수 목록에 들어가면 이 엔드포인트는 아무도 호출할 수 없게 된다. 대조군으로 같은 base path의
 * logout은 인증이 필요해 토큰 없이는 401이어야 한다.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class})
class AuthEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private UserStatusResolver userStatusResolver;
    @MockitoBean
    private AuditLogger auditLogger;

    @Test
    void 탈퇴_철회는_토큰_없이도_접근할_수_있다() throws Exception {
        when(authService.reactivate(any())).thenReturn(new LoginResponse("access-token", "refresh-token", 1800L));

        mockMvc.perform(post("/api/auth/reactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"withdrawn@test.com\",\"password\":\"Abcd1234!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    void 대조군_로그아웃은_토큰_없이는_401이다() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * AUTH-03 — 비밀번호 재설정 요청도 login/signup/reactivate와 같은 인증 전 요청이다. 실수로 인증
     * 필수 목록에 들어가면 비밀번호를 잊은(=로그인할 수 없는) 사용자가 이 기능 자체를 쓸 수 없게 된다.
     */
    @Test
    void 비밀번호_재설정_요청은_토큰_없이도_접근할_수_있다() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@test.com\"}"))
                .andExpect(status().isOk());
    }
}
