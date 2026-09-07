package com.jiseong.homesense.complex.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.complex.dto.ComplexDetailResponse;
import com.jiseong.homesense.complex.dto.ComplexMapPointResponse;
import com.jiseong.homesense.complex.dto.ComplexMapSearchResponse;
import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;
import com.jiseong.homesense.complex.exception.ComplexNotFoundException;
import com.jiseong.homesense.complex.service.ComplexService;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

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
    private AuditLogger auditLogger;

    private static ComplexSummaryResponse summary(Long id) {
        return new ComplexSummaryResponse(id, "테스트단지", "서울특별시", "강남구", "역삼동", 500, (short) 5,
                LocalDate.of(2010, 1, 1), HousingType.APT, DealCategory.SALE, LocalDate.of(2026, 1, 10), 120000L,
                new java.math.BigDecimal("84.99"));
    }

    @Test
    void 검색에_성공하면_200과_목록_및_pageMeta를_반환한다() throws Exception {
        when(complexService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(summary(1L)), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/complexes/search").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].complexId").value(1))
                .andExpect(jsonPath("$.pageMeta.totalElements").value(1));
    }

    @Test
    void sort_값이_허용_목록_밖이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/complexes/search").param("sort", "POPULARITY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SORT_CONDITION"));
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
        ComplexDetailResponse response = new ComplexDetailResponse(1L, "테스트단지", "아파트", "서울특별시", "강남구", "역삼동",
                "서울특별시 강남구 역삼동 123", new java.math.BigDecimal("37.5"), new java.math.BigDecimal("127.0"),
                "PRECISE", false, basicInfo, extendedInfo);
        when(complexService.getDetail(1L)).thenReturn(response);

        mockMvc.perform(get("/api/complexes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complexId").value(1))
                .andExpect(jsonPath("$.data.matchPending").value(false))
                .andExpect(jsonPath("$.data.basicInfo.householdCount").value(500))
                .andExpect(jsonPath("$.data.extendedInfo.managementType").value("위탁관리"));
    }

    @Test
    void 존재하지_않는_단지면_404를_반환한다() throws Exception {
        when(complexService.getDetail(999L)).thenThrow(new ComplexNotFoundException());

        mockMvc.perform(get("/api/complexes/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMPLEX_NOT_FOUND"));
    }

    @Test
    void 지도_범위_조회에_성공하면_200과_포인트_목록을_반환한다() throws Exception {
        ComplexMapSearchResponse response = new ComplexMapSearchResponse(
                List.of(new ComplexMapPointResponse(1L, "테스트단지", new java.math.BigDecimal("37.5"),
                        new java.math.BigDecimal("127.0"), "PRECISE")),
                false);
        when(complexService.searchInBounds(any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/complexes/map").param("bounds", "37.0,127.0,37.1,127.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.points[0].complexId").value(1));
    }

    @Test
    void bounds_형식이_잘못되면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/complexes/map").param("bounds", "not-valid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_BOUNDS"));
    }

    @Test
    void bounds_파라미터가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/complexes/map"))
                .andExpect(status().isBadRequest());
    }
}
