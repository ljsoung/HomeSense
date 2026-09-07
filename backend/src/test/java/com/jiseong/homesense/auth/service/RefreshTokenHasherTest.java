package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenHasherTest {

    private final RefreshTokenHasher hasher = new RefreshTokenHasher();

    @Test
    void 같은_원문은_항상_같은_해시를_반환한다() {
        assertThat(hasher.hash("same-token")).isEqualTo(hasher.hash("same-token"));
    }

    @Test
    void 다른_원문은_다른_해시를_반환한다() {
        assertThat(hasher.hash("token-a")).isNotEqualTo(hasher.hash("token-b"));
    }

    @Test
    void SHA_256_16진수_64자를_반환한다() {
        assertThat(hasher.hash("token")).hasSize(64).matches("^[0-9a-f]{64}$");
    }
}
