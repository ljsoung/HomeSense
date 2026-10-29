package com.jiseong.homesense.region.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.jiseong.homesense.common.security.AccessTokenEpochService;
import com.jiseong.homesense.common.security.UserStatusResolver;
import com.jiseong.homesense.common.security.UserPrincipal;
import com.jiseong.homesense.region.dto.InterestRegionSummaryResponse;
import com.jiseong.homesense.region.dto.RegionAutocompleteResponse;
import com.jiseong.homesense.region.exception.RegionNotFoundException;
import com.jiseong.homesense.region.service.RegionService;

/*
 * addFilters=false라 SecurityConfig의 authenticated() 규칙 자체(비로그인 401)는 여기서 검증하지
 * 않는다(CLAUDE.md 트러블슈팅 노트, UserControllerTest와 같은 접근) — interestSummary()는
 * @AuthenticationPrincipal이 Controller까지 정확히 전달되는지만 확인한다.
 */
@WebMvcTest(controllers = RegionController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionService regionService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private UserStatusResolver userStatusResolver;
    @MockitoBean
    private AccessTokenEpochService accessTokenEpochService;
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
    void 자동완성에_성공하면_200과_후보_목록을_반환한다() throws Exception {
        when(regionService.autocomplete("역삼"))
                .thenReturn(List.of(new RegionAutocompleteResponse("1168010100", "서울특별시 강남구 역삼동")));

        mockMvc.perform(get("/api/regions").param("query", "역삼"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].legalDongCd").value("1168010100"))
                .andExpect(jsonPath("$.data[0].fullPath").value("서울특별시 강남구 역삼동"));
    }

    @Test
    void query가_없으면_빈_문자열로_Service에_위임한다() throws Exception {
        when(regionService.autocomplete("")).thenReturn(List.of());

        mockMvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 관심지역_요약_조회에_성공하면_200과_목록을_반환한다() throws Exception {
        when(regionService.getInterestSummary(1L)).thenReturn(List.of(
                new InterestRegionSummaryResponse(10L, "1168010100", "서울특별시 강남구 역삼동",
                        new BigDecimal("110000"), new BigDecimal("10.00"), 23L)));

        mockMvc.perform(get("/api/regions/interest-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].favoriteRegionId").value(10))
                .andExpect(jsonPath("$.data[0].avgPrice").value(110000))
                .andExpect(jsonPath("$.data[0].changeRate").value(10.00))
                .andExpect(jsonPath("$.data[0].tradeCount").value(23));
    }

    @Test
    void 관심지역이_없으면_200과_빈_목록을_반환한다() throws Exception {
        when(regionService.getInterestSummary(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/regions/interest-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 코드로_지역을_조회하면_200과_전체_경로를_반환한다() throws Exception {
        when(regionService.lookup("1168010100"))
                .thenReturn(new RegionAutocompleteResponse("1168010100", "서울특별시 강남구 역삼동"));

        mockMvc.perform(get("/api/regions/1168010100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legalDongCd").value("1168010100"))
                .andExpect(jsonPath("$.data.fullPath").value("서울특별시 강남구 역삼동"));
    }

    @Test
    void 없는_코드면_404_REGION_NOT_FOUND를_반환한다() throws Exception {
        when(regionService.lookup("2811000000")).thenThrow(new RegionNotFoundException());

        mockMvc.perform(get("/api/regions/2811000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REGION_NOT_FOUND"));
    }

    /** 리터럴 경로가 경로 변수보다 먼저 매칭돼야 한다 — lookup()이 "interest-summary"를 코드로 받으면 안 된다. */
    @Test
    void interest_summary는_코드_조회로_매칭되지_않는다() throws Exception {
        when(regionService.getInterestSummary(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/regions/interest-summary")).andExpect(status().isOk());

        verify(regionService, never()).lookup(any());
    }
}
