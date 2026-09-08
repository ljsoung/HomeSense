package com.jiseong.homesense.trade.dto;

import java.util.Locale;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.RentType;

/**
 * SVC-TRD-01.getHistory() 거래유형 필터(DTL-01 이력 탭 — 전체/매매/전세/월세). dealCategory만으로는
 * JEONSE/WOLSE를 구분할 수 없어 rentType까지 함께 인코딩한다.
 *
 * <p>{@link #from(String)}은 값이 없거나 허용 목록(SALE/JEONSE/WOLSE) 밖이면 예외 없이 {@link #ALL}로
 * 폴백한다 — 처리 로직 표(CLAUDE.md 스타일 설계서)가 getHistory()에 열거하는 예외는
 * MissingComplexIdException/TradeNotFoundException 둘뿐이라, 잘못된 dealType 값까지 예외로
 * 다루면 표에 없는 예외를 임의로 추가하는 셈이 된다 — DTL-01 탭이 고정된 값만 보내는 내부용
 * 파라미터인 점도 감안했다(지성 확인 필요 — 값 검증이 필요해지면 이 자리에 예외를 추가하라).
 */
public enum DealTypeFilter {
    ALL(null, null),
    SALE(DealCategory.SALE, null),
    JEONSE(DealCategory.RENT, RentType.JEONSE),
    WOLSE(DealCategory.RENT, RentType.WOLSE);

    private final DealCategory dealCategory;
    private final RentType rentType;

    DealTypeFilter(DealCategory dealCategory, RentType rentType) {
        this.dealCategory = dealCategory;
        this.rentType = rentType;
    }

    public DealCategory dealCategory() {
        return dealCategory;
    }

    public RentType rentType() {
        return rentType;
    }

    public static DealTypeFilter from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return DealTypeFilter.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}
