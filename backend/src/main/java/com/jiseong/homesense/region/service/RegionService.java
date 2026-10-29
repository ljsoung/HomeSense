package com.jiseong.homesense.region.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.common.cache.CacheNames;
import com.jiseong.homesense.favorite.repository.FavoriteRegionRepository;
import com.jiseong.homesense.region.dto.InterestRegionSummaryResponse;
import com.jiseong.homesense.region.dto.RegionAutocompleteResponse;
import com.jiseong.homesense.region.exception.RegionNotFoundException;
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

    /** 법정동코드 형식(10자리 ASCII 숫자). 전각 숫자 등 유니코드 숫자는 받지 않는다. */
    private static final Pattern LEGAL_DONG_CD = Pattern.compile("[0-9]{10}");

    private final LegalDistrictCodeRepository legalDistrictCodeRepository;
    private final FavoriteRegionRepository favoriteRegionRepository;
    private final RegionStatsCalculator regionStatsCalculator;

    @Cacheable(cacheNames = CacheNames.REGION_AUTOCOMPLETE, key = "#query")
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

    /**
     * 법정동코드 하나의 이름(fullPath)을 돌려준다 — SRCH-01이 URL에 regionCode만 있을 때(직접 진입·새로고침·MY-04 알림 링크)
     * 검색창·제목에 지역 이름을 보이려고 부른다. 계층과 무관하게 활성 행이면 돌려준다(단지 검색 regionCode와 같은 범위).
     * 형식이 틀리거나 없거나 폐지된 코드는 모두 404 {@link RegionNotFoundException} — 형식 오류도 "그런 지역 없음"이라
     * 400으로 나누지 않는다(호출자는 어느 쪽이든 이름 없이 진행한다). 예외는 캐시되지 않는다.
     */
    @Cacheable(cacheNames = CacheNames.REGION_LOOKUP, key = "#legalDongCd")
    public RegionAutocompleteResponse lookup(String legalDongCd) {
        if (legalDongCd == null || !LEGAL_DONG_CD.matcher(legalDongCd).matches()) {
            throw new RegionNotFoundException();
        }
        return legalDistrictCodeRepository.findByLegalDongCdAndIsActiveTrue(legalDongCd)
                .map(RegionAutocompleteResponse::from)
                .orElseThrow(RegionNotFoundException::new);
    }

    public List<InterestRegionSummaryResponse> getInterestSummary(Long userId) {
        return favoriteRegionRepository.findByUser_UserId(userId).stream()
                .map(favorite -> InterestRegionSummaryResponse.of(favorite,
                        regionStatsCalculator.calculate(favorite.getLegalDistrictCode().getLegalDongCd())))
                .toList();
    }
}
