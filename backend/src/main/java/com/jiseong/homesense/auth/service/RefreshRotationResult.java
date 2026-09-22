package com.jiseong.homesense.auth.service;

import com.jiseong.homesense.auth.dto.TokenResponse;

/**
 * {@link RefreshTokenRotator#attempt}의 결과 — 회전 성공(새 토큰 쌍) 또는 재사용 탐지(사용자 ID)
 * 둘 중 하나다. 예외로 표현하지 않는 이유는 {@link AuthService#refreshAccessToken}이 재사용 탐지
 * 시점에 {@link RefreshTokenReuseHandler}(별도 트랜잭션)를 호출해야 하는데, 그 호출은 반드시
 * {@code attempt()}의 트랜잭션이 완전히 끝난(커밋된) *뒤에* 일어나야 하기 때문이다(락 충돌로 인한
 * 자기 교착을 피하기 위함 — {@link RefreshTokenReuseHandler}의 javadoc 참고). 예외를 여기서 던지면
 * 호출자가 catch하는 시점에도 {@code attempt()}의 트랜잭션(과 그 안에서 잡은 락)이 아직 열려 있어
 * 이 문제를 해결하지 못한다.
 */
sealed interface RefreshRotationResult {

    record Rotated(TokenResponse tokenResponse) implements RefreshRotationResult {
    }

    record ReuseDetected(Long userId) implements RefreshRotationResult {
    }
}
