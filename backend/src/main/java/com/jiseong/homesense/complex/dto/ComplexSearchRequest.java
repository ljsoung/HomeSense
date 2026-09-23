package com.jiseong.homesense.complex.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import com.jiseong.homesense.common.validation.SearchKeywordPolicy;
import com.jiseong.homesense.complex.exception.InvalidRegionCodeException;
import com.jiseong.homesense.complex.exception.MissingSearchConditionException;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.RentType;

/**
 * SRCH-01 검색 요청. regionCode·keyword 중 하나를 제외하면 전부 선택적이며, 생략하면 해당 조건은 걸지 않는다.
 *
 * <p>거래유형은 trade 값 체계를 그대로 따른다: 매매 {@code dealCategory=SALE}, 전세
 * {@code rentType=JEONSE}, 월세 {@code rentType=WOLSE}(dealCategory=RENT는 생략해도 된다 — rentType만으로
 * 전월세로 취급). amountMin/amountMax는 매매면 trade.dealAmount, 전월세면 trade.depositAmount(보증금)
 * 기준이다. 월세금액은 필터 대상이 아니다.
 *
 * <p>buildYearMin/buildYearMax는 trade가 아니라 complex.approval_date(사용승인일) 기준이다(UI정의서 4.4절).
 *
 * <p><b>regionCode와 keyword 중 하나는 필수다</b> — 둘 다 없으면 400 MISSING_SEARCH_CONDITION.
 *
 * <p>regionCode는 지역 자동완성(GET /api/regions)의 legalDongCd를 그대로 받는다 — 10자리 숫자가 아니면
 * 400, 존재하지 않거나 폐지된 코드는 빈 결과다. keyword는 단지명·주소 텍스트 부분 일치 필터이고
 * 검색 기록은 남기지 않는다(기록은 POST /api/search/logs, CLAUDE.md "단지 검색 지역코드·키워드" 절).
 */
public record ComplexSearchRequest(
        List<HousingType> housingTypes,
        DealCategory dealCategory,
        RentType rentType,
        BigDecimal areaMin,
        BigDecimal areaMax,
        Long amountMin,
        Long amountMax,
        Short buildYearMin,
        Short buildYearMax,
        String regionCode,
        String keyword,
        String sort) {

    private static final Pattern REGION_CODE = Pattern.compile("[0-9]{10}");

    /**
     * 형식 검증(regionCode·keyword·sort)을 먼저 하고, 마지막에 "regionCode와 keyword 중 하나는 있어야
     * 한다"를 검사한다 — 형식이 틀린 값은 "조건 없음"보다 구체적인 오류로 알려주기 위해서다. 공백뿐인
     * keyword는 조건 없음으로 보므로 regionCode 없이 보내면 MISSING_SEARCH_CONDITION이다.
     */
    public ComplexSearchCondition toCondition() {
        ComplexSearchCondition condition = ComplexSearchCondition.builder()
                .housingTypes(housingTypes)
                .dealCategory(dealCategory)
                .rentType(rentType)
                .areaMin(areaMin)
                .areaMax(areaMax)
                .amountMin(amountMin)
                .amountMax(amountMax)
                .buildYearMin(buildYearMin)
                .buildYearMax(buildYearMax)
                .regionCode(validRegionCode())
                .keyword(SearchKeywordPolicy.normalizeOptional(keyword))
                .sort(SortCondition.from(sort))
                .build();
        if (condition.regionCode() == null && condition.keyword() == null) {
            throw new MissingSearchConditionException();
        }
        return condition;
    }

    private String validRegionCode() {
        if (regionCode == null || regionCode.isBlank()) {
            return null;
        }
        String trimmed = regionCode.trim();
        if (!REGION_CODE.matcher(trimmed).matches()) {
            throw new InvalidRegionCodeException();
        }
        return trimmed;
    }
}
