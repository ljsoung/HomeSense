package com.jiseong.homesense.region.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jiseong.homesense.common.response.ApiResponse;
import com.jiseong.homesense.common.security.UserPrincipal;
import com.jiseong.homesense.region.dto.InterestRegionSummaryResponse;
import com.jiseong.homesense.region.dto.RegionAutocompleteResponse;
import com.jiseong.homesense.region.service.RegionService;

import lombok.RequiredArgsConstructor;

/**
 * API-RGN-01. base path: /api/regions. autocomplete()는 비로그인 조회 허용, interestSummary()는
 * 로그인 회원 전용(SecurityConfig에서 GET /api/regions/interest-summary만 authenticated()로 막음).
 */
@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @GetMapping
    public ApiResponse<List<RegionAutocompleteResponse>> autocomplete(
            @RequestParam(defaultValue = "") String query) {
        return ApiResponse.success(regionService.autocomplete(query));
    }

    @GetMapping("/interest-summary")
    public ApiResponse<List<InterestRegionSummaryResponse>> interestSummary(
            @AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.success(regionService.getInterestSummary(me.userId()));
    }
}
