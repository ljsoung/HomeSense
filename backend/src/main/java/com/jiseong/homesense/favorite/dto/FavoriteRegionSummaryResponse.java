package com.jiseong.homesense.favorite.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.jiseong.homesense.favorite.entity.FavoriteRegion;
import com.jiseong.homesense.region.dto.RegionAutocompleteResponse;
import com.jiseong.homesense.region.dto.RegionStats;
import com.jiseong.homesense.region.entity.LegalDistrictCode;

/**
 * MY-02 관심 지역 리스트 카드 한 건. RGN 도메인의 {@code RegionStatsCalculator}를 그대로
 * 재사용한다(설계서 Service 표 "RGN 도메인 집계 로직 재사용") — HOME-01 전용
 * {@code InterestRegionSummaryResponse}와 같은 RegionStats를 소비하지만, MY-02가 추가로 요구하는
 * "3.3㎡당 평균가"·"신규거래 건수"까지 함께 노출한다는 점이 다르다(CLAUDE.md SVC-RGN-01 절 —
 * FAV-01/MY-02 착수 시 완결하기로 미리 표시해 둔 필드).
 *
 * <p>sidoName·sigunguName·eupmyeondongName(2026-10-02 MY-02 착수 시 추가): 카드가 읍면동명을 크게, 시도·시군구를
 * 보조로 나눠 보여 준다 — 같은 이름의 동이 여러 시군구에 있어 읍면동명만으로는 구분되지 않고, fullPath를
 * 공백으로 쪼개면 "수원시 장안구"처럼 공백이 든 시군구에서 틀린다. sigunguName은 세종처럼 시군구 계층이 없으면
 * null이다. registeredAt은 "등록순" 정렬 기준이다.
 */
public record FavoriteRegionSummaryResponse(
        Long favoriteRegionId,
        String legalDongCd,
        String fullPath,
        String sidoName,
        String sigunguName,
        String eupmyeondongName,
        LocalDateTime registeredAt,
        BigDecimal avgPrice,
        BigDecimal changeRate,
        BigDecimal pricePerPyeong,
        long newTradeCount) {

    public static FavoriteRegionSummaryResponse of(FavoriteRegion favoriteRegion, RegionStats stats) {
        LegalDistrictCode code = favoriteRegion.getLegalDistrictCode();
        RegionAutocompleteResponse region = RegionAutocompleteResponse.from(code);
        return new FavoriteRegionSummaryResponse(
                favoriteRegion.getFavoriteRegionId(),
                region.legalDongCd(),
                region.fullPath(),
                code.getSidoName(),
                code.getSigunguName(),
                code.getEupmyeondongName(),
                favoriteRegion.getRegisteredAt(),
                stats.avgPrice(),
                stats.changeRate(),
                stats.pricePerPyeong(),
                stats.newTradeCount());
    }
}
