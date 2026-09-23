package com.jiseong.homesense.complex.dto;

import java.math.BigDecimal;
import java.util.List;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.RentType;

import lombok.Builder;

/**
 * SVC-CPX-01.search() 입력. {@link ComplexSearchRequest#toCondition()}이 형식 검증을 마친 값만 담는다.
 *
 * <ul>
 *   <li>regionCode: 10자리 법정동코드. Service가 {@code RegionCodePrefixResolver}로 계층 prefix를 구해
 *       {@code complex.legal_dong_cd LIKE 'prefix%'}로 거른다.</li>
 *   <li>keyword: trim·길이 검증을 마친 값. 단지명·주소 텍스트 부분 일치 필터.</li>
 *   <li>rentType: JEONSE/WOLSE. 지정하면 dealCategory가 비어 있어도 전월세로 취급한다
 *       (TradeSearchCondition과 같은 값 체계·같은 규칙).</li>
 * </ul>
 */
@Builder(toBuilder = true)
public record ComplexSearchCondition(
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
        SortCondition sort) {

    /** RENT(보증금 기준) 여부 — dealCategory=RENT이거나 rentType이 지정된 경우. */
    public boolean isRent() {
        return dealCategory == DealCategory.RENT || rentType != null;
    }
}
