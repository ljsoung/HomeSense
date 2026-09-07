package com.jiseong.homesense.user.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import com.jiseong.homesense.common.exception.InvalidCredentialsException;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.common.security.UserPrincipal;
import com.jiseong.homesense.user.dto.UpdateUserCommand;
import com.jiseong.homesense.user.dto.UserResponse;
import com.jiseong.homesense.user.dto.WithdrawCommand;
import com.jiseong.homesense.user.exception.UserNotFoundException;
import com.jiseong.homesense.user.service.UserService;

/*
 * addFilters=false로는 SecurityConfig의 authenticated() 규칙 자체는 검증되지 않는다(CLAUDE.md
 * 트러블슈팅 노트) — 여기서는 @AuthenticationPrincipal이 UserPrincipal을 Controller까지 정확히
 * 전달하는지만 확인하고, 인증 미충족 401 자체는 SecurityConfig/RestAuthenticationEntryPoint의
 * 책임으로 남긴다(AuthControllerTest의 로그아웃 테스트와 같은 접근).
 */
@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private AuditLogger auditLogger;

    private static final UserPrincipal ME = new UserPrincipal(1L, "USER");

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ME, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 내_정보_조회에_성공하면_200과_회원정보를_반환한다() throws Exception {
        when(userService.getUser(1L))
                .thenReturn(new UserResponse(1L, "user@test.com", "닉네임", LocalDateTime.of(2026, 1, 1, 0, 0)));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("user@test.com"))
                .andExpect(jsonPath("$.data.nickname").value("닉네임"));
    }

    @Test
    void 존재하지_않는_회원이면_404를_반환한다() throws Exception {
        when(userService.getUser(1L)).thenThrow(new UserNotFoundException());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 내_정보_수정에_성공하면_200과_수정된_정보를_반환한다() throws Exception {
        when(userService.updateUser(eq(1L), eq(new UpdateUserCommand("새닉네임", null, null))))
                .thenReturn(new UserResponse(1L, "user@test.com", "새닉네임", LocalDateTime.of(2026, 1, 1, 0, 0)));

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"새닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));
    }

    @Test
    void 닉네임이_정책을_위반하면_400과_필드에러를_반환한다() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"));
    }

    @Test
    void 닉네임_생략은_유효하다() throws Exception {
        when(userService.updateUser(eq(1L), eq(new UpdateUserCommand(null, "current1!", "New1234!"))))
                .thenReturn(new UserResponse(1L, "user@test.com", "닉네임", LocalDateTime.of(2026, 1, 1, 0, 0)));

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"current1!\",\"newPassword\":\"New1234!\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 현재비밀번호가_불일치하면_401을_반환한다() throws Exception {
        when(userService.updateUser(eq(1L), eq(new UpdateUserCommand(null, "wrong", "New1234!"))))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"New1234!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void 탈퇴에_성공하면_인증된_사용자ID로_서비스를_호출한다() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"correct\",\"reason\":\"더 이상 사용하지 않음\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).withdraw(eq(1L), eq(new WithdrawCommand("correct")));
    }

    @Test
    void 탈퇴_비밀번호가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"사유\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
