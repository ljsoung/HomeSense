package com.jiseong.homesense.complex.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.complex.exception.InvalidSortConditionException;

class SortConditionTest {

    @Test
    void null이거나_공백이면_LATEST를_기본값으로_쓴다() {
        assertThat(SortCondition.from(null)).isEqualTo(SortCondition.LATEST);
        assertThat(SortCondition.from(" ")).isEqualTo(SortCondition.LATEST);
    }

    @Test
    void 허용_목록_값은_대소문자와_무관하게_통과한다() {
        assertThat(SortCondition.from("LATEST")).isEqualTo(SortCondition.LATEST);
        assertThat(SortCondition.from("amount")).isEqualTo(SortCondition.AMOUNT);
        assertThat(SortCondition.from("Area")).isEqualTo(SortCondition.AREA);
    }

    @Test
    void 허용_목록_밖의_값이면_InvalidSortConditionException을_던진다() {
        assertThatThrownBy(() -> SortCondition.from("POPULARITY")).isInstanceOf(InvalidSortConditionException.class);
    }
}
