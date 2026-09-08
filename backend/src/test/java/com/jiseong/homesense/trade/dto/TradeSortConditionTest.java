package com.jiseong.homesense.trade.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.trade.exception.InvalidSortConditionException;

class TradeSortConditionTest {

    @Test
    void null이거나_공백이면_LATEST를_기본값으로_쓴다() {
        assertThat(TradeSortCondition.from(null)).isEqualTo(TradeSortCondition.LATEST);
        assertThat(TradeSortCondition.from(" ")).isEqualTo(TradeSortCondition.LATEST);
    }

    @Test
    void 허용_목록_값은_대소문자와_무관하게_통과한다() {
        assertThat(TradeSortCondition.from("LATEST")).isEqualTo(TradeSortCondition.LATEST);
        assertThat(TradeSortCondition.from("amount")).isEqualTo(TradeSortCondition.AMOUNT);
        assertThat(TradeSortCondition.from("Area")).isEqualTo(TradeSortCondition.AREA);
    }

    @Test
    void 허용_목록_밖의_값이면_InvalidSortConditionException을_던진다() {
        assertThatThrownBy(() -> TradeSortCondition.from("POPULARITY"))
                .isInstanceOf(InvalidSortConditionException.class);
    }
}
