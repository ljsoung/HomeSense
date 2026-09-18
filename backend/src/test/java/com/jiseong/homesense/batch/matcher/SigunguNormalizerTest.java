package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link ComplexMasterMatcher}(BAT-MAT-02 1차 필터링)와 {@link RegionCoverageChecker}(재적재 후 경량
 * 커버리지 체크) 양쪽이 공유하는 정규화 규칙 자체를 직접 검증한다 — 두 소비자가 늘어나며 이 로직이
 * {@code ComplexMasterMatcher}에서 이 클래스로 추출됐다(CLAUDE.md 2026년 법정동코드 재적재 세션 참고).
 */
class SigunguNormalizerTest {

    @Test
    void 시_구_구조는_공백을_제거하고_문자열_끝이_아닌_시를_제거한다() {
        assertThat(SigunguNormalizer.normalize("수원시 장안구")).isEqualTo("수원장안구");
        assertThat(SigunguNormalizer.normalize("경기도 화성시 만세구".substring("경기도 ".length())))
                .isEqualTo("화성만세구");
    }

    @Test
    void 공백이_없으면_시가_몇_번_등장하든_그대로_반환한다() {
        assertThat(SigunguNormalizer.normalize("시흥시")).isEqualTo("시흥시");
        assertThat(SigunguNormalizer.normalize("목포시")).isEqualTo("목포시");
        assertThat(SigunguNormalizer.normalize("종로구")).isEqualTo("종로구");
    }

    @Test
    void null은_그대로_null을_반환한다() {
        assertThat(SigunguNormalizer.normalize(null)).isNull();
    }
}
