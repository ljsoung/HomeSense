package com.jiseong.homesense.complex.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jiseong.homesense.complex.dto.BoundsCondition;
import com.jiseong.homesense.complex.dto.ComplexDetailResponse;
import com.jiseong.homesense.complex.dto.ComplexMapSearchResponse;
import com.jiseong.homesense.complex.dto.ComplexSearchRequest;
import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;
import com.jiseong.homesense.complex.dto.MapFilterRequest;
import com.jiseong.homesense.complex.exception.InvalidLimitException;
import com.jiseong.homesense.complex.service.ComplexService;
import com.jiseong.homesense.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * API-CPX-01. base path: /api/complexes. 전부 비로그인 조회 허용(인증 불필요).
 *
 * <p>search()/map()의 실제 반환 타입은 설계서 표의 {@code PageResponse<T>}/{@code List<T>}와 다르다 —
 * PageResponse는 COM-RES-01의 기존 {@code ApiResponse.success(Page<T>)}(data+pageMeta) 관례를 그대로
 * 쓰고, map()은 truncated 플래그를 실을 자리가 필요해 {@link ComplexMapSearchResponse}로 감쌌다
 * (CLAUDE.md SVC-CPX-01 절 참고).
 */
@RestController
@RequestMapping("/api/complexes")
@RequiredArgsConstructor
public class ComplexController {

    private static final int DEFAULT_POPULAR_LIMIT = 8;

    /**
     * limit 상한 — getPopular()는 값마다 최대 2건(findById + 대표 거래 조회)의 추가 쿼리를 내는 N+1
     * 경로라, 인증 없는 이 엔드포인트가 임의로 큰 limit을 그대로 받으면 그만큼 쿼리가 배로 늘어난다
     * (예: limit=100000 → 최대 약 20만 건). PageRequest.of(0, limit)는 0 이하도 IllegalArgumentException으로
     * 500을 내므로 하한도 함께 막는다(코드리뷰에서 지적됨).
     */
    private static final int MIN_POPULAR_LIMIT = 1;
    private static final int MAX_POPULAR_LIMIT = 50;

    private final ComplexService complexService;

    @GetMapping("/search")
    public ApiResponse<List<ComplexSummaryResponse>> search(@ModelAttribute ComplexSearchRequest cond, Pageable pageable) {
        return ApiResponse.success(complexService.search(cond.toCondition(), pageable));
    }

    @GetMapping("/popular")
    public ApiResponse<List<ComplexSummaryResponse>> popular(
            @RequestParam(defaultValue = "" + DEFAULT_POPULAR_LIMIT) int limit) {
        if (limit < MIN_POPULAR_LIMIT || limit > MAX_POPULAR_LIMIT) {
            throw new InvalidLimitException();
        }
        return ApiResponse.success(complexService.getPopular(limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<ComplexDetailResponse> getDetail(@PathVariable Long id) {
        return ApiResponse.success(complexService.getDetail(id));
    }

    @GetMapping("/map")
    public ApiResponse<ComplexMapSearchResponse> map(@RequestParam String bounds, @ModelAttribute MapFilterRequest filter) {
        return ApiResponse.success(complexService.searchInBounds(BoundsCondition.parse(bounds), filter.toCondition()));
    }
}
