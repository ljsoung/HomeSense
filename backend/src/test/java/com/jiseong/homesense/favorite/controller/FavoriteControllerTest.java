package com.jiseong.homesense.favorite.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

import com.jiseong.homesense.common.exception.ComplexNotFoundException;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.common.security.UserPrincipal;
import com.jiseong.homesense.favorite.dto.AddFavoritePropertyCommand;
import com.jiseong.homesense.favorite.dto.AddFavoriteRegionCommand;
import com.jiseong.homesense.favorite.dto.FavoritePropertyResponse;
import com.jiseong.homesense.favorite.dto.FavoritePropertySummaryResponse;
import com.jiseong.homesense.favorite.dto.FavoriteRegionResponse;
import com.jiseong.homesense.favorite.dto.FavoriteRegionSummaryResponse;
import com.jiseong.homesense.favorite.exception.DuplicateFavoriteException;
import com.jiseong.homesense.favorite.exception.MissingComplexIdException;
import com.jiseong.homesense.favorite.service.FavoriteService;
import com.jiseong.homesense.trade.entity.HousingType;

/*
 * addFilters=false라 SecurityConfig의 authenticated() 규칙 자체는 검증하지 않는다(CLAUDE.md
 * 트러블슈팅 노트, UserControllerTest와 같은 접근) — @AuthenticationPrincipal이 Controller까지
 * userId를 정확히 전달하는지, 그리고 Service가 던지는 예외를 COM-RES-01 포맷으로 옮기는지만 확인한다.
 */
@WebMvcTest(controllers = FavoriteController.class)
@AutoConfigureMockMvc(addFilters = false)
class FavoriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FavoriteService favoriteService;

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
    void 관심매물_목록_조회는_인증된_사용자ID로_조회한다() throws Exception {
        when(favoriteService.getFavoriteProperties(1L)).thenReturn(List.of(
                new FavoritePropertySummaryResponse(100L, 10L, "테스트단지", "서울특별시", "강남구", "역삼동",
                        HousingType.APT, null, null, null, null, false)));

        mockMvc.perform(get("/api/favorites/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].complexId").value(10));
    }

    @Test
    void 관심매물_등록에_성공하면_200과_등록결과를_반환한다() throws Exception {
        when(favoriteService.addFavoriteProperty(eq(1L), eq(new AddFavoritePropertyCommand(10L))))
                .thenReturn(new FavoritePropertyResponse(100L, 10L, "테스트단지", HousingType.APT, LocalDateTime.now()));

        mockMvc.perform(post("/api/favorites/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"complexId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favoritePropertyId").value(100));
    }

    @Test
    void 관심매물_등록시_complexId가_없으면_400을_반환한다() throws Exception {
        when(favoriteService.addFavoriteProperty(eq(1L), eq(new AddFavoritePropertyCommand(null))))
                .thenThrow(new MissingComplexIdException());

        mockMvc.perform(post("/api/favorites/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_COMPLEX_ID"));
    }

    @Test
    void 관심매물_중복등록이면_409를_반환한다() throws Exception {
        when(favoriteService.addFavoriteProperty(eq(1L), eq(new AddFavoritePropertyCommand(10L))))
                .thenThrow(new DuplicateFavoriteException());

        mockMvc.perform(post("/api/favorites/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"complexId\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_FAVORITE"));
    }

    @Test
    void 존재하지_않는_단지로_관심매물_등록시_404를_반환한다() throws Exception {
        when(favoriteService.addFavoriteProperty(eq(1L), eq(new AddFavoritePropertyCommand(999L))))
                .thenThrow(new ComplexNotFoundException());

        mockMvc.perform(post("/api/favorites/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"complexId\":999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMPLEX_NOT_FOUND"));
    }

    @Test
    void 관심매물_삭제에_성공하면_인증된_사용자ID로_서비스를_호출한다() throws Exception {
        mockMvc.perform(delete("/api/favorites/properties/{id}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(favoriteService).removeFavoriteProperty(eq(1L), eq(100L));
    }

    @Test
    void 관심지역_목록_조회는_인증된_사용자ID로_조회한다() throws Exception {
        when(favoriteService.getFavoriteRegions(1L)).thenReturn(List.of(
                new FavoriteRegionSummaryResponse(200L, "1168010100", "서울특별시 강남구 역삼동",
                        new BigDecimal("110000"), new BigDecimal("10.00"), new BigDecimal("3000"), 4L)));

        mockMvc.perform(get("/api/favorites/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].legalDongCd").value("1168010100"))
                .andExpect(jsonPath("$.data[0].newTradeCount").value(4));
    }

    @Test
    void 관심지역_등록에_성공하면_200과_등록결과를_반환한다() throws Exception {
        when(favoriteService.addFavoriteRegion(eq(1L), eq(new AddFavoriteRegionCommand("1168010100"))))
                .thenReturn(new FavoriteRegionResponse(200L, "1168010100", "서울특별시 강남구 역삼동", LocalDateTime.now()));

        mockMvc.perform(post("/api/favorites/regions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalDongCd\":\"1168010100\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favoriteRegionId").value(200));
    }

    @Test
    void 관심지역_등록시_legalDongCd가_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/favorites/regions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalDongCd\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 관심지역_삭제에_성공하면_인증된_사용자ID로_서비스를_호출한다() throws Exception {
        mockMvc.perform(delete("/api/favorites/regions/{id}", 200L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(favoriteService).removeFavoriteRegion(eq(1L), eq(200L));
    }
}
