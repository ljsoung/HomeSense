package com.jiseong.homesense.trade.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.RentType;

class DealTypeFilterTest {

    @Test
    void null이거나_공백이면_ALL을_반환한다() {
        assertThat(DealTypeFilter.from(null)).isEqualTo(DealTypeFilter.ALL);
        assertThat(DealTypeFilter.from(" ")).isEqualTo(DealTypeFilter.ALL);
    }

    @Test
    void 허용_목록_밖의_값이면_예외_없이_ALL로_폴백한다() {
        assertThat(DealTypeFilter.from("MONTHLY")).isEqualTo(DealTypeFilter.ALL);
    }

    @Test
    void SALE은_dealCategory만_걸고_rentType은_걸지_않는다() {
        assertThat(DealTypeFilter.SALE.dealCategory()).isEqualTo(DealCategory.SALE);
        assertThat(DealTypeFilter.SALE.rentType()).isNull();
    }

    @Test
    void JEONSE와_WOLSE는_RENT와_각각의_rentType을_함께_건다() {
        assertThat(DealTypeFilter.from("jeonse").dealCategory()).isEqualTo(DealCategory.RENT);
        assertThat(DealTypeFilter.from("jeonse").rentType()).isEqualTo(RentType.JEONSE);
        assertThat(DealTypeFilter.from("wolse").rentType()).isEqualTo(RentType.WOLSE);
    }

    @Test
    void ALL은_아무_조건도_걸지_않는다() {
        assertThat(DealTypeFilter.ALL.dealCategory()).isNull();
        assertThat(DealTypeFilter.ALL.rentType()).isNull();
    }
}
