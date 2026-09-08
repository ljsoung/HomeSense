package com.jiseong.homesense.trade.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.jiseong.homesense.trade.dto.DealTypeFilter;
import com.jiseong.homesense.trade.dto.TradeDetailResponse;
import com.jiseong.homesense.trade.dto.TradeResponse;
import com.jiseong.homesense.trade.dto.TradeSummaryResponse;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.RentType;
import com.jiseong.homesense.trade.exception.TradeNotFoundException;
import com.jiseong.homesense.trade.service.TradeService;

@WebMvcTest(controllers = TradeController.class)
@AutoConfigureMockMvc(addFilters = false)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradeService tradeService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private AuditLogger auditLogger;

    private static TradeSummaryResponse summary(Long id) {
        return new TradeSummaryResponse(id, 1L, "테스트단지", null, HousingType.APT, DealCategory.SALE, null,
                "서울특별시", "강남구", "역삼동", LocalDate.of(2026, 1, 10), new BigDecimal("84.90"), (short) 5,
                80000L, null);
    }

    private static TradeResponse history(Long id, boolean cancelled) {
        return new TradeResponse(id, LocalDate.of(2026, 1, 10), DealCategory.SALE, null,
                new BigDecimal("84.90"), (short) 5, 80000L, null, null, cancelled, false);
    }

    @Test
    void 검색에_성공하면_200과_목록_및_pageMeta를_반환한다() throws Exception {
        when(tradeService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(summary(1L)), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/trades/search").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].tradeId").value(1))
                .andExpect(jsonPath("$.pageMeta.totalElements").value(1));
    }

    @Test
    void sort_값이_허용_목록_밖이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/trades/search").param("sort", "POPULARITY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SORT_CONDITION"));
    }

    @Test
    void 이력조회에_성공하면_200과_목록을_반환한다() throws Exception {
        when(tradeService.getHistory(eq(1L), isNull(), eq(DealTypeFilter.ALL)))
                .thenReturn(List.of(history(10L, false)));

        mockMvc.perform(get("/api/trades").param("complexId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tradeId").value(10))
                .andExpect(jsonPath("$.data[0].isCancelled").value(false));
    }

    @Test
    void complexId가_없으면_Service의_MissingComplexIdException이_400으로_변환된다() throws Exception {
        when(tradeService.getHistory(isNull(), isNull(), eq(DealTypeFilter.ALL)))
                .thenThrow(new com.jiseong.homesense.trade.exception.MissingComplexIdException());

        mockMvc.perform(get("/api/trades"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_COMPLEX_ID"));
    }

    @Test
    void housingType과_dealType_파라미터를_파싱해_전달한다() throws Exception {
        when(tradeService.getHistory(eq(1L), eq(HousingType.APT), eq(DealTypeFilter.JEONSE)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/trades")
                        .param("complexId", "1")
                        .param("housingType", "apt")
                        .param("dealType", "jeonse"))
                .andExpect(status().isOk());
    }

    @Test
    void housingType이나_dealType이_허용_목록_밖이면_조용히_무시한다() throws Exception {
        when(tradeService.getHistory(eq(1L), isNull(), eq(DealTypeFilter.ALL))).thenReturn(List.of());

        mockMvc.perform(get("/api/trades")
                        .param("complexId", "1")
                        .param("housingType", "OFFICETEL")
                        .param("dealType", "UNKNOWN"))
                .andExpect(status().isOk());
    }

    @Test
    void 취소된_거래는_isCancelled_true로_그대로_노출된다() throws Exception {
        when(tradeService.getHistory(eq(1L), isNull(), eq(DealTypeFilter.ALL)))
                .thenReturn(List.of(history(10L, true)));

        mockMvc.perform(get("/api/trades").param("complexId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isCancelled").value(true));
    }

    @Test
    void 상세조회에_성공하면_200과_상세정보를_반환한다() throws Exception {
        TradeDetailResponse response = new TradeDetailResponse(1L, LocalDate.of(2026, 1, 10),
                new BigDecimal("84.90"), (short) 5, DealCategory.SALE, null, 80000L, null, null,
                null, true, "중개거래", "개인", "개인", null, false, null, false);
        when(tradeService.getDetail(1L)).thenReturn(response);

        mockMvc.perform(get("/api/trades/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tradeId").value(1))
                .andExpect(jsonPath("$.data.aptDongPending").value(true))
                .andExpect(jsonPath("$.data.dealingType").value("중개거래"));
    }

    @Test
    void 존재하지_않는_거래면_404를_반환한다() throws Exception {
        when(tradeService.getDetail(999L)).thenThrow(new TradeNotFoundException());

        mockMvc.perform(get("/api/trades/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRADE_NOT_FOUND"));
    }

    @Test
    void RENT_거래는_rentType과_월세금액을_함께_노출한다() throws Exception {
        TradeSummaryResponse rentSummary = new TradeSummaryResponse(2L, 1L, "테스트단지", null, HousingType.APT,
                DealCategory.RENT, RentType.WOLSE, "서울특별시", "강남구", "역삼동", LocalDate.of(2026, 1, 10),
                new BigDecimal("59.90"), (short) 3, 10000L, 50L);
        when(tradeService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(rentSummary), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/trades/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].rentType").value("WOLSE"))
                .andExpect(jsonPath("$.data[0].monthlyRentAmount").value(50));
    }
}
