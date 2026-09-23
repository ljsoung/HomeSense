package com.jiseong.homesense.common.cache;

/**
 * COM-CACHE-01 캐시 이름. {@code @Cacheable}/{@code CacheEvictionListener}/유지보수 러너가 같은 문자열을
 * 따로 적어 두면 버전업(예: complexDetail → complexDetailV2) 때 한쪽만 바뀌는 드리프트가 생기므로 여기서 모은다.
 */
public final class CacheNames {

    /** SVC-CPX-01.getDetail() — ComplexDetailResponse(matchPending, 주소 포함). */
    public static final String COMPLEX_DETAIL = "complexDetailV2";
    /**
     * SVC-CPX-01.getPopular() — ComplexSummaryResponse(sido/sigungu/dongRi 포함). V3: rentType·monthlyRentAmount
     * 추가(2026-09-23) — 옛 V2 엔트리를 읽으면 두 필드가 조용히 null이 되므로 이름을 올렸다.
     */
    public static final String POPULAR_COMPLEXES = "popularComplexesV3";
    /** SVC-RGN-01.autocomplete() — RegionAutocompleteResponse(legalDongCd, fullPath). */
    public static final String REGION_AUTOCOMPLETE = "regionAutocomplete";

    private CacheNames() {
    }
}
