package com.jiseong.homesense.batch.notifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class LegalDongHierarchyTest {

    @Test
    void 리_코드는_읍면_시군구_시도까지_펼친다() {
        assertThat(LegalDongHierarchy.ancestorsAndSelf("4155025021"))
                .containsExactly("4155025021", "4155025000", "4155000000", "4100000000");
    }

    @Test
    void 동_코드() {
        assertThat(LegalDongHierarchy.ancestorsAndSelf("1111017400"))
                .containsExactly("1111017400", "1111000000", "1100000000");
    }

    @Test
    void 구를_가진_시의_동은_구와_시를_모두_낸다() {
        // 수원시 장안구 파장동 → 장안구(41111), 수원시(4111)
        assertThat(LegalDongHierarchy.ancestorsAndSelf("4111112900"))
                .containsExactly("4111112900", "4111100000", "4111000000", "4100000000");
    }

    @Test
    void 읍면_코드와_시군구_코드() {
        assertThat(LegalDongHierarchy.ancestorsAndSelf("4155025000"))
                .containsExactly("4155025000", "4155000000", "4100000000");
        assertThat(LegalDongHierarchy.ancestorsAndSelf("1168000000")).containsExactly("1168000000", "1100000000");
    }

    @Test
    void 형식이_틀린_코드는_무시한다() {
        assertThat(LegalDongHierarchy.ancestorsAndSelf((String) null)).isEmpty();
        assertThat(LegalDongHierarchy.ancestorsAndSelf("12345")).isEmpty();
        assertThat(LegalDongHierarchy.ancestorsAndSelf(List.of("1111017400", "abc"))).hasSize(3);
    }
}
