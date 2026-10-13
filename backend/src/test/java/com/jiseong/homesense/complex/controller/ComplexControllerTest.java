package com.jiseong.homesense.complex.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jiseong.homesense.common.exception.ComplexNotFoundException;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.AccessTokenEpochService;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.common.security.UserPrincipal;
import com.jiseong.homesense.common.security.UserStatusResolver;
import com.jiseong.homesense.complex.dto.BoundsCondition;
import com.jiseong.homesense.complex.dto.ComplexDetailResponse;
import com.jiseong.homesense.complex.dto.ComplexMapPointResponse;
import com.jiseong.homesense.complex.dto.ComplexMapSearchResponse;
import com.jiseong.homesense.complex.dto.ComplexSearchCondition;
import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;
import com.jiseong.homesense.complex.service.ComplexService;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.MatchMethod;
import com.jiseong.homesense.trade.entity.RentType;

@WebMvcTest(controllers = ComplexController.class)
@AutoConfigureMockMvc(addFilters = false)
class ComplexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplexService complexService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private UserStatusResolver userStatusResolver;
    @MockitoBean
    private AccessTokenEpochService accessTokenEpochService;
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

    private static ComplexSummaryResponse summary(Long id) {
        return new ComplexSummaryResponse(id, "테스트단지", "서울특별시", "강남구", "역삼동", 500, (short) 5,
                LocalDate.of(2010, 1, 1), HousingType.APT, DealCategory.SALE, LocalDate.of(2026, 1, 10), 120000L,
                new java.math.BigDecimal("84.99"), com.jiseong.homesense.trade.entity.MatchMethod.EXACT, (short) 12,
                null, null);
    }

    @Test
    void 검색에_성공하면_200과_목록_및_pageMeta를_반환한다() throws Exception {
        when(complexService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(summary(1L)), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/complexes/search").param("regionCode", "4111100000")
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].complexId").value(1))
                .andExpect(jsonPath("$.data[0].matchMethod").value("EXACT"))
                .andExpect(jsonPath("$.data[0].floor").value(12))
                .andExpect(jsonPath("$.pageMeta.totalElements").value(1));
    }

    @Test
    void sort_값이_허용_목록_밖이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/complexes/search").param("sort", "POPULARITY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SORT_CONDITION"));
    }

    @Test
    void regionCode_keyword_거래유형_필터를_조건으로_바인딩한다() throws Exception {
        when(complexService.search(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/complexes/search")
                        .param("regionCode", "4111100000")
                        .param("keyword", "  래미안  ")
                        .param("rentType", "JEONSE")
                        .param("amountMin", "30000"))
                .andExpect(status().isOk());

        ArgumentCaptor<ComplexSearchCondition> captor = ArgumentCaptor.forClass(ComplexSearchCondition.class);
        verify(complexService).search(captor.capture(), any());
        assertThat(captor.getValue().regionCode()).isEqualTo("4111100000");
        assertThat(captor.getValue().keyword()).isEqualTo("래미안");
        assertThat(captor.getValue().rentType()).isEqualTo(RentType.JEONSE);
        assertThat(captor.getValue().isRent()).isTrue();
    }

    @Test
    void 공백뿐인_keyword는_조건_없음으로_처리한다() throws Exception {
        when(complexService.search(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/complexes/search").param("regionCode", "4111100000").param("keyword", "   "))
                .andExpect(status().isOk());

        ArgumentCaptor<ComplexSearchCondition> captor = ArgumentCaptor.forClass(ComplexSearchCondition.class);
        verify(complexService).search(captor.capture(), any());
        assertThat(captor.getValue().keyword()).isNull();
    }

    @Test
    void regionCode와_keyword가_둘_다_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/complexes/search").param("rentType", "JEONSE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_SEARCH_CONDITION"));
        mockMvc.perform(get("/api/complexes/search").param("keyword", "   ").param("regionCode", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_SEARCH_CONDITION"));
        verify(complexService, never()).search(any(), any());
    }

    @Test
    void keyword만_있어도_검색한다() throws Exception {
        when(complexService.search(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/complexes/search").param("keyword", "래미안")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"411110000", "41111000000", "41111a0000", "４１１１１０００００"})
    void regionCode가_10자리_숫자가_아니면_400을_반환한다(String regionCode) throws Exception {
        mockMvc.perform(get("/api/complexes/search").param("regionCode", regionCode))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REGION_CODE"));
        verify(complexService, never()).search(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", " a ", "123456789012345678901234567890123456789012345678901"})
    void keyword가_2자_미만이거나_50자를_넘으면_400을_반환한다(String keyword) throws Exception {
        mockMvc.perform(get("/api/complexes/search").param("keyword", keyword))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SEARCH_KEYWORD"));
        verify(complexService, never()).search(any(), any());
    }

    @Test
    void 인기단지_조회에_성공하면_기본_limit로_서비스를_호출한다() throws Exception {
        when(complexService.getPopular(8)).thenReturn(List.of(summary(1L), summary(2L)));

        mockMvc.perform(get("/api/complexes/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void 인기단지_limit_파라미터를_그대로_전달한다() throws Exception {
        when(complexService.getPopular(3)).thenReturn(List.of(summary(1L)));

        mockMvc.perform(get("/api/complexes/popular").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 인기단지_limit이_0이면_400을_반환하고_서비스를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/complexes/popular").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_LIMIT"));

        verify(complexService, never()).getPopular(anyInt());
    }

    @Test
    void 인기단지_limit이_음수면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/complexes/popular").param("limit", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_LIMIT"));
    }

    @Test
    void 인기단지_limit이_상한을_넘으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/complexes/popular").param("limit", "100000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_LIMIT"));

        verify(complexService, never()).getPopular(anyInt());
    }

    @Test
    void 인기단지_limit이_상한과_같으면_통과한다() throws Exception {
        when(complexService.getPopular(50)).thenReturn(List.of());

        mockMvc.perform(get("/api/complexes/popular").param("limit", "50"))
                .andExpect(status().isOk());
    }

    @Test
    void 상세조회에_성공하면_200과_상세정보를_반환한다() throws Exception {
        ComplexDetailResponse.BasicInfo basicInfo =
                new ComplexDetailResponse.BasicInfo(500, (short) 5, LocalDate.of(2010, 1, 1), "시공사", 600, (short) 20);
        ComplexDetailResponse.ExtendedInfo extendedInfo = new ComplexDetailResponse.ExtendedInfo(
                "분양", 480, 20, 10, 10, "위탁관리", "개별난방", "복도식", "철근콘크리트", "개발사", "관리회사",
                (short) 2, (short) 1, (short) 0, 400, 200, true, false, (short) 10, (short) 5, (short) 30, true,
                "커뮤니티", "편의시설", (short) 20, (short) 2, "서울시 강남구", "02-1234-5678");
        ComplexDetailResponse response = new ComplexDetailResponse(1L, "테스트단지", "아파트", HousingType.APT,
                "서울특별시", "강남구", "역삼동", "1168010100", "서울특별시 강남구 역삼동 123", new java.math.BigDecimal("37.5"),
                new java.math.BigDecimal("127.0"), "PRECISE", false, MatchMethod.EXACT, basicInfo, extendedInfo);
        when(complexService.getDetail(eq(1L), any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/complexes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complexId").value(1))
                .andExpect(jsonPath("$.data.matchPending").value(false))
                .andExpect(jsonPath("$.data.matchMethod").value("EXACT"))
                .andExpect(jsonPath("$.data.legalDongCd").value("1168010100"))
                .andExpect(jsonPath("$.data.basicInfo.householdCount").value(500))
                .andExpect(jsonPath("$.data.extendedInfo.managementType").value("위탁관리"));
    }

    @Test
    void 상세조회_시_비로그인_세션헤더를_그대로_Service에_전달한다() throws Exception {
        when(complexService.getDetail(eq(1L), isNull(), eq("session-abc"))).thenReturn(minimalDetail());

        mockMvc.perform(get("/api/complexes/1").header("X-Session-Id", "session-abc"))
                .andExpect(status().isOk());

        verify(complexService).getDetail(1L, null, "session-abc");
    }

    @Test
    void 상세조회_시_로그인_상태면_userId를_Service에_전달한다() throws Exception {
        authenticate();
        when(complexService.getDetail(eq(1L), eq(1L), any())).thenReturn(minimalDetail());

        mockMvc.perform(get("/api/complexes/1"))
                .andExpect(status().isOk());

        verify(complexService).getDetail(1L, 1L, null);
    }

    private static ComplexDetailResponse minimalDetail() {
        ComplexDetailResponse.BasicInfo basicInfo =
                new ComplexDetailResponse.BasicInfo(500, (short) 5, LocalDate.of(2010, 1, 1), "시공사", 600, (short) 20);
        ComplexDetailResponse.ExtendedInfo extendedInfo = new ComplexDetailResponse.ExtendedInfo(
                "분양", 480, 20, 10, 10, "위탁관리", "개별난방", "복도식", "철근콘크리트", "개발사", "관리회사",
                (short) 2, (short) 1, (short) 0, 400, 200, true, false, (short) 10, (short) 5, (short) 30, true,
                "커뮤니티", "편의시설", (short) 20, (short) 2, "서울시 강남구", "02-1234-5678");
        return new ComplexDetailResponse(1L, "테스트단지", "아파트", HousingType.APT,
                "서울특별시", "강남구", "역삼동", "1168010100", "서울특별시 강남구 역삼동 123", new java.math.BigDecimal("37.5"),
                new java.math.BigDecimal("127.0"), "PRECISE", false, MatchMethod.EXACT, basicInfo, extendedInfo);
    }

    @Test
    void 존재하지_않는_단지면_404를_반환한다() throws Exception {
        when(complexService.getDetail(eq(999L), any(), any())).thenThrow(new ComplexNotFoundException());

        mockMvc.perform(get("/api/complexes/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMPLEX_NOT_FOUND"));
    }

    @Test
    void 지도_범위_조회에_성공하면_200과_items_truncated_limit을_반환한다() throws Exception {
        ComplexMapSearchResponse response = new ComplexMapSearchResponse(
                List.of(new ComplexMapPointResponse(1L, "테스트단지", "서울특별시", "강남구", "역삼동", 500, (short) 5,
                        LocalDate.of(2010, 1, 1), HousingType.APT, DealCategory.RENT, LocalDate.of(2026, 9, 1),
                        5000L, new java.math.BigDecimal("84.90"), MatchMethod.SIMILAR, (short) 7, RentType.WOLSE, 120L,
                        new java.math.BigDecimal("37.5000000"), new java.math.BigDecimal("127.0300000"), "APPROXIMATE")),
                true, 500);
        when(complexService.searchInBounds(any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/complexes/map").param("bounds", "37.0,127.0,37.1,127.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.truncated").value(true))
                .andExpect(jsonPath("$.data.limit").value(500))
                .andExpect(jsonPath("$.data.items[0].complexId").value(1))
                .andExpect(jsonPath("$.data.items[0].dongRi").value("역삼동"))
                .andExpect(jsonPath("$.data.items[0].latitude").value(37.5))
                .andExpect(jsonPath("$.data.items[0].longitude").value(127.03))
                .andExpect(jsonPath("$.data.items[0].locationPrecision").value("APPROXIMATE"))
                .andExpect(jsonPath("$.data.items[0].matchMethod").value("SIMILAR"))
                .andExpect(jsonPath("$.data.items[0].representativeAmount").value(5000))
                .andExpect(jsonPath("$.data.items[0].monthlyRentAmount").value(120))
                .andExpect(jsonPath("$.data.items[0].rentType").value("WOLSE"))
                .andExpect(jsonPath("$.data.items[0].representativeArea").value(84.9))
                .andExpect(jsonPath("$.data.items[0].floor").value(7))
                .andExpect(jsonPath("$.data.items[0].representativeDealDate").value("2026-09-01"))
                .andExpect(jsonPath("$.data.pageMeta").doesNotExist());
    }

    @Test
    void 지도_범위_조회는_SRCH01과_같은_이름의_필터를_검색_조건으로_넘긴다() throws Exception {
        when(complexService.searchInBounds(any(), any())).thenReturn(new ComplexMapSearchResponse(List.of(), false, 500));

        mockMvc.perform(get("/api/complexes/map").param("bounds", " 37.0, 126.9 ,37.1,127.1")
                        .param("housingTypes", "APT").param("housingTypes", "VILLA")
                        .param("rentType", "JEONSE")
                        .param("areaMin", "59.5").param("areaMax", "85")
                        .param("amountMin", "10000").param("amountMax", "50000")
                        .param("buildYearMin", "2000").param("buildYearMax", "2020")
                        .param("regionCode", "1168000000").param("keyword", "무시됨"))
                .andExpect(status().isOk());

        ArgumentCaptor<BoundsCondition> bounds = ArgumentCaptor.forClass(BoundsCondition.class);
        ArgumentCaptor<ComplexSearchCondition> condition = ArgumentCaptor.forClass(ComplexSearchCondition.class);
        verify(complexService).searchInBounds(bounds.capture(), condition.capture());
        assertThat(bounds.getValue().toString()).isEqualTo("37.0,126.9,37.1,127.1");
        ComplexSearchCondition c = condition.getValue();
        assertThat(c.housingTypes()).containsExactly(HousingType.APT, HousingType.VILLA);
        assertThat(c.rentType()).isEqualTo(RentType.JEONSE);
        assertThat(c.isRent()).isTrue();
        assertThat(c.areaMin()).isEqualByComparingTo("59.5");
        assertThat(c.areaMax()).isEqualByComparingTo("85");
        assertThat(c.amountMin()).isEqualTo(10000L);
        assertThat(c.amountMax()).isEqualTo(50000L);
        assertThat(c.buildYearMin()).isEqualTo((short) 2000);
        assertThat(c.buildYearMax()).isEqualTo((short) 2020);
        // 지역은 bounds가 정한다 — regionCode·keyword는 조건에 실리지 않는다.
        assertThat(c.regionCode()).isNull();
        assertThat(c.keyword()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-valid", "37.0,127.0,37.1", "37.0,127.0,37.1,127.1,", "37.0,127.0,37.1,abc", "37.0,,37.1,127.1",
            "90.1,127.0,91,127.1", "-91,127.0,37.1,127.1", "37.0,-180.5,37.1,127.1", "37.0,127.0,37.1,181",
            "37.2,127.0,37.1,127.1", "37.0,127.2,37.1,127.1", "NaN,127.0,37.1,127.1", "1e400,127.0,37.1,127.1", " "})
    void bounds_형식_오류_범위_밖_남서가_북동보다_크면_400_INVALID_BOUNDS를_반환한다(String bounds) throws Exception {
        mockMvc.perform(get("/api/complexes/map").param("bounds", bounds))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_BOUNDS"));
        verify(complexService, never()).searchInBounds(any(), any());
    }

    @Test
    void bounds_파라미터가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/complexes/map"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bounds가_남서와_북동이_같은_점이어도_허용한다() throws Exception {
        when(complexService.searchInBounds(any(), any())).thenReturn(new ComplexMapSearchResponse(List.of(), false, 500));

        mockMvc.perform(get("/api/complexes/map").param("bounds", "37.5,127.0,37.5,127.0"))
                .andExpect(status().isOk());
    }
}
