package com.jiseong.homesense.recentview.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.common.security.UserPrincipal;
import com.jiseong.homesense.recentview.dto.RecentViewResponse;
import com.jiseong.homesense.recentview.service.RecentViewService;
import com.jiseong.homesense.trade.entity.HousingType;

/*
 * addFilters=false라 SecurityConfig 자체는 검증하지 않는다(CLAUDE.md 트러블슈팅 노트,
 * RegionControllerTest와 같은 접근) — @AuthenticationPrincipal이 있을 때/없을 때 각각
 * Controller가 userId를 올바르게 뽑아 Service에 넘기는지만 확인한다.
 */
@WebMvcTest(controllers = RecentViewController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecentViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecentViewService recentViewService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private AuditLogger auditLogger;

    private static final UserPrincipal ME = new UserPrincipal(1L, "USER");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ME, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @Test
    void 로그인_상태면_userId_기준으로_조회한다() throws Exception {
        authenticate();
        when(recentViewService.getRecent(eq(1L), isNull(), eq(3))).thenReturn(
                List.of(new RecentViewResponse(10L, "테스트단지", HousingType.APT, LocalDateTime.now())));

        mockMvc.perform(get("/api/recent-views"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].complexId").value(10));
    }

    @Test
    void 비로그인_상태면_세션헤더_기준으로_조회한다() throws Exception {
        when(recentViewService.getRecent(isNull(), eq("session-abc"), eq(3))).thenReturn(
                List.of(new RecentViewResponse(20L, "테스트단지2", HousingType.VILLA, LocalDateTime.now())));

        mockMvc.perform(get("/api/recent-views").header("X-Session-Id", "session-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].complexId").value(20));
    }

    @Test
    void userId도_세션헤더도_없으면_빈_목록을_반환한다() throws Exception {
        when(recentViewService.getRecent(isNull(), isNull(), eq(3))).thenReturn(List.of());

        mockMvc.perform(get("/api/recent-views"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void limit_파라미터를_그대로_전달한다() throws Exception {
        authenticate();
        when(recentViewService.getRecent(eq(1L), isNull(), eq(5))).thenReturn(List.of());

        mockMvc.perform(get("/api/recent-views").param("limit", "5"))
                .andExpect(status().isOk());
    }
}
