package com.jiseong.homesense.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogMaskingTest {

    @Test
    void 이메일은_첫_글자와_최상위_도메인만_남긴다() {
        assertThat(LogMasking.mask("Duplicate entry 'alice.kim@example.com' for key 'uk_user_email'"))
                .isEqualTo("Duplicate entry 'a***@e***.com' for key 'uk_user_email'");
        assertThat(LogMasking.mask("x@y.co.kr, b+tag@sub.domain.io")).isEqualTo("x***@y***.kr, b***@s***.io");
    }

    @Test
    void Bearer_토큰과_JWT를_가린다() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3In0.abc-DEF_123";

        assertThat(LogMasking.mask("Authorization: Bearer " + jwt)).isEqualTo("Authorization: Bearer ***");
        assertThat(LogMasking.mask("refreshToken=" + jwt + " end")).isEqualTo("refreshToken=***JWT*** end");
    }

    @Test
    void 재설정_링크의_token_파라미터를_가린다() {
        assertThat(LogMasking.mask("https://hmss.site/password-reset?token=0123abcd&x=1"))
                .isEqualTo("https://hmss.site/password-reset?token=***&x=1");
    }

    @Test
    void 가릴_것이_없으면_그대로다() {
        assertThat(LogMasking.mask("BATCH_FAILURE context=COM-EXC-01")).isEqualTo("BATCH_FAILURE context=COM-EXC-01");
        assertThat(LogMasking.mask(null)).isNull();
        assertThat(LogMasking.mask("")).isEmpty();
    }
}
