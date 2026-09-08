package com.jiseong.homesense.trade.controller;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jiseong.homesense.common.response.ApiResponse;
import com.jiseong.homesense.trade.dto.DealTypeFilter;
import com.jiseong.homesense.trade.dto.TradeDetailResponse;
import com.jiseong.homesense.trade.dto.TradeResponse;
import com.jiseong.homesense.trade.dto.TradeSearchRequest;
import com.jiseong.homesense.trade.dto.TradeSummaryResponse;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.service.TradeService;

import lombok.RequiredArgsConstructor;

/**
 * API-TRD-01. base path: /api/trades. 전부 비로그인 조회 허용(인증 불필요).
 *
 * <p>search()의 실제 반환 타입은 설계서 표의 {@code PageResponse<T>}와 다르다 — 프로젝트 어디에도
 * PageResponse 클래스가 없어, COM-RES-01이 이미 정의한 {@code ApiResponse.success(Page<T>)}
 * (data+pageMeta) 관례를 ComplexController.search()와 동일하게 재사용한다(CLAUDE.md SVC-CPX-01
 * 절 "GET /search·/map 응답 타입" 참고 — 이 프로젝트에서 이미 확정된 관례).
 */
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    @GetMapping("/search")
    public ApiResponse<List<TradeSummaryResponse>> search(@ModelAttribute TradeSearchRequest cond, Pageable pageable) {
        return ApiResponse.success(tradeService.search(cond.toCondition(), pageable));
    }

    /**
     * DTL-01 단지별 실거래 이력. complexId는 필수이며 NULL이면 Service가 MissingComplexIdException을
     * 던진다. housingType/dealType 문자열이 비어있거나 허용 목록 밖이면 그 조건은 걸지 않고 조용히
     * 무시한다 — 설계서 예외 처리표가 이 두 파라미터의 검증 실패를 별도 예외로 다루지 않는다.
     */
    @GetMapping
    public ApiResponse<List<TradeResponse>> getHistory(
            @RequestParam(required = false) Long complexId,
            @RequestParam(required = false) String housingType,
            @RequestParam(required = false) String dealType) {
        return ApiResponse.success(
                tradeService.getHistory(complexId, parseHousingType(housingType), DealTypeFilter.from(dealType)));
    }

    @GetMapping("/{tradeId}")
    public ApiResponse<TradeDetailResponse> getDetail(@PathVariable Long tradeId) {
        return ApiResponse.success(tradeService.getDetail(tradeId));
    }

    private HousingType parseHousingType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return HousingType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
