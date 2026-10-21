package com.jiseong.homesense.auth.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 없는 이메일로 로그인할 때 비교할 BCrypt 해시. 비교를 건너뛰면 응답 시간 차이(BCrypt 한 번)로 가입 여부가 드러난다 — 없는
 * 이메일도 실제 회원과 같은 비용의 {@link PasswordEncoder#matches}를 한 번 거치게 한다. 해시는 기동 시 같은 인코더(같은 cost)로
 * 한 번 만든다. 원문은 무작위라 어떤 입력과도 일치하지 않는다.
 */
@Component
class DummyPasswordHash {

    private final PasswordEncoder passwordEncoder;
    private final String hash;

    DummyPasswordHash(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.hash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** 실제 회원과 같은 비용으로 비교만 하고 결과는 버린다. */
    void compare(String rawPassword) {
        passwordEncoder.matches(rawPassword, hash);
    }
}
