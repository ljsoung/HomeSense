package com.jiseong.homesense.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jiseong.homesense.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenValue(String tokenValue);

    /**
     * SVC-USER-01.withdraw() — 회원 탈퇴 시 그 회원의 모든 Refresh Token을 재로그인 차단을 위해
     * 일괄 폐기한다. 엔티티를 하나씩 불러 revoke()를 호출하는 대신 단일 UPDATE로 처리한다(설계서
     * "일괄 갱신" 표현과 일치).
     *
     * <p>{@code flushAutomatically = true}가 반드시 필요하다 — 호출부(UserService.withdraw())는 이
     * 쿼리 직전에 {@code user.withdraw()}로 User 엔티티를 dirty 상태로만 만들어 두는데, 이 쿼리의
     * query space는 refresh_token뿐이라(Hibernate가 {@code r.user.userId}를 refresh_token의 FK
     * 컬럼으로 직접 풀어내 user 테이블과 join하지 않는다) Hibernate의 자동 flush-before-query가 그
     * User 변경을 감지하지 못해 flush하지 않는다. 그 상태에서 {@code clearAutomatically = true}만
     * 걸려 있으면 이 쿼리 직후 영속성 컨텍스트가 clear되며 아직 flush되지 않은 User의 변경(status=
     * WITHDRAWN)이 DB에 한 번도 반영되지 못한 채 그대로 버려진다 — refresh_token은 정상적으로
     * 폐기됐는데 user.status는 ACTIVE로 남아, 탈퇴한 사용자가 즉시 재로그인할 수 있게 되는 심각한
     * 결함이었다(코드리뷰에서 지적됨, 검증: UserServiceMariaDbIT). flushAutomatically로 이 쿼리
     * 실행 전에 먼저 flush를 강제해 User의 변경을 선반영시킨 뒤에야 clearAutomatically가 안전하다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedYn = true WHERE r.user.userId = :userId AND r.revokedYn = false")
    void revokeAllByUserId(@Param("userId") Long userId);

    /**
     * SVC-AUTH-01.refreshAccessToken() Rotation — "조회 후 폐기"의 TOCTOU를 막는 조건부 UPDATE다.
     * 같은(아직 유효해 보이는) Refresh Token으로 두 요청이 거의 동시에 재발급을 시도하면, 조회
     * (findByTokenValue) 시점엔 둘 다 유효해 보이지만 이 UPDATE는 그중 하나만 먼저
     * {@code revoked_yn=false → true}로 성공시키고(영향받은 행=1), 늦게 도착한 쪽은 0을 받는다 — 진
     * 쪽은 "이미 폐기된 토큰의 재사용"과 정확히 같은 신호이므로 재사용 탐지(전체 폐기 + 감사 로그)로
     * 넘긴다. {@code reactivateIfWithinGrace}(UserRepository)와 같은 "affected rows로 경합 판정"
     * 패턴이다.
     *
     * <p>형제 메서드들과 달리 {@code clearAutomatically}를 걸지 않는다 — 이 쿼리는 조회해 둔
     * {@code RefreshToken}(stored)만 갱신할 뿐, 호출부가 이후에도 계속 써야 하는 {@code User} 엔티티는
     * 건드리지 않는다. 영속성 컨텍스트를 비우면 그 User 참조가 detach돼, 성공 경로에서 그 User를
     * 참조로 넘겨 새 RefreshToken을 저장할 때 불필요한 위험을 만든다 — 이 쿼리 이후 stored를 다시
     * 읽는 코드가 없어 clear로 얻을 이점도 없다. {@code flushAutomatically}는 방어적으로 유지한다
     * (호출 시점에 flush할 대상이 없어 지금은 사실상 no-op이지만, 다른 형제 메서드들과의 일관성을
     * 위해).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedYn = true WHERE r.refreshTokenId = :id AND r.revokedYn = false")
    int revokeIfUnrevoked(@Param("id") Long refreshTokenId);
}
