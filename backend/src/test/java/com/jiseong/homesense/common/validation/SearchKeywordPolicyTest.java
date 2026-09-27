package com.jiseong.homesense.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.common.exception.InvalidSearchKeywordException;

class SearchKeywordPolicyTest {

    @Test
    void 선택_검색어는_null이나_공백이면_조건_없음이다() {
        assertThat(SearchKeywordPolicy.normalizeOptional(null)).isNull();
        assertThat(SearchKeywordPolicy.normalizeOptional("   ")).isNull();
    }

    @Test
    void 앞뒤_공백을_제거한다() {
        assertThat(SearchKeywordPolicy.normalizeOptional("  금도동 ")).isEqualTo("금도동");
    }

    @Test
    void 길이는_trim_이후_코드포인트_기준_2자_이상_50자_이하다() {
        assertThat(SearchKeywordPolicy.normalizeOptional("안성")).isEqualTo("안성");
        assertThat(SearchKeywordPolicy.normalizeOptional("가".repeat(50))).hasSize(50);
        assertThatThrownBy(() -> SearchKeywordPolicy.normalizeOptional(" 안 "))
                .isInstanceOf(InvalidSearchKeywordException.class);
        assertThatThrownBy(() -> SearchKeywordPolicy.normalizeOptional("가".repeat(51)))
                .isInstanceOf(InvalidSearchKeywordException.class);
        // 이모지 1개는 UTF-16으로 2칸이지만 1자다.
        assertThatThrownBy(() -> SearchKeywordPolicy.normalizeOptional("😀"))
                .isInstanceOf(InvalidSearchKeywordException.class);
    }

    @Test
    void 기록용_검색어는_비어_있으면_400이다() {
        assertThatThrownBy(() -> SearchKeywordPolicy.normalizeRequired(null))
                .isInstanceOf(InvalidSearchKeywordException.class);
        assertThatThrownBy(() -> SearchKeywordPolicy.normalizeRequired("  "))
                .isInstanceOf(InvalidSearchKeywordException.class);
        assertThat(SearchKeywordPolicy.normalizeRequired(" 안성시 ")).isEqualTo("안성시");
    }
}
