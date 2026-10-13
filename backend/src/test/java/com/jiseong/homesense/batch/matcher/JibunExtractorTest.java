package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JibunExtractorTest {

    @Test
    void 단일_토큰은_전체가_지번이어야_한다() {
        assertThat(JibunExtractor.parseStandalone("123-4")).map(JibunExtractor.Jibun::key).contains("123-4");
        assertThat(JibunExtractor.parseStandalone("산 45-6")).map(JibunExtractor.Jibun::key).contains("산45-6");
        assertThat(JibunExtractor.parseStandalone("가-238")).isEmpty();
        assertThat(JibunExtractor.parseStandalone(null)).isEmpty();
    }

    @Test
    void 앞에서_읽을_때는_뒤에_붙은_글자를_무시한다() {
        assertThat(JibunExtractor.parseLeading(" 73- 경희궁의아침4단지")).map(JibunExtractor.Jibun::number).contains("73");
        assertThat(JibunExtractor.parseLeading("산 12-3 마을")).map(JibunExtractor.Jibun::display).contains("산 12-3");
        assertThat(JibunExtractor.parseLeading("신내역 힐데스하임")).isEmpty();
    }

    @Test
    void 동리_뒤의_지번을_읽는다() {
        assertThat(JibunExtractor.afterDongRi("서울특별시 종로구 숭인동 766 종로청계힐스테이트", "숭인동"))
                .map(JibunExtractor.Jibun::key).contains("766");
        assertThat(JibunExtractor.afterDongRi("서울특별시 종로구 숭인동 766", null)).isEmpty();
    }
}
