package com.jiseong.homesense.region.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.favorite.repository.FavoriteRegionRepository;
import com.jiseong.homesense.region.dto.InterestRegionSummaryResponse;
import com.jiseong.homesense.region.dto.RegionAutocompleteResponse;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;

import lombok.RequiredArgsConstructor;

/**
 * SVC-RGN-01. 법정동코드 기준 자동완성과 로그인 회원의 관심 지역 시세 요약을 담당한다.
 * autocomplete()는 COM-CACHE-01 캐시(regionAutocomplete::{query}, TTL 24h)를 적용한다 — 무효화는
 * BAT-MAT-01이 발행하는 LegalDistrictCodeReloadedEvent를 CacheEvictionListener가 구독한다
 * (일 단위 TradeCacheEvictionEvent에 묶지 않는다, CLAUDE.md 캐싱 절 참고).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {

    /** UIC-03 자동완성 최소 입력 길이 — 이보다 짧으면 DB 조회 없이 빈 리스트를 반환한다. */
    private static final int MIN_QUERY_LENGTH = 2;

    /** UIC-03 자동완성 후보 상한. */
    private static final int AUTOCOMPLETE_LIMIT = 10;

    private final LegalDistrictCodeRepository legalDistrictCodeRepository;
    private final FavoriteRegionRepository favoriteRegionRepository;
    private final RegionStatsCalculator regionStatsCalculator;

    @Cacheable(cacheNames = "regionAutocomplete", key = "#query")
    public List<RegionAutocompleteResponse> autocomplete(String query) {
        if (query == null || query.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        return legalDistrictCodeRepository
                .searchByNameContaining(query, PageRequest.of(0, AUTOCOMPLETE_LIMIT))
                .stream()
                .map(RegionAutocompleteResponse::from)
                .toList();
    }

    public List<InterestRegionSummaryResponse> getInterestSummary(Long userId) {
        return favoriteRegionRepository.findByUser_UserId(userId).stream()
                .map(favorite -> InterestRegionSummaryResponse.of(favorite,
                        regionStatsCalculator.calculate(favorite.getLegalDistrictCode().getLegalDongCd())))
                .toList();
    }
}
