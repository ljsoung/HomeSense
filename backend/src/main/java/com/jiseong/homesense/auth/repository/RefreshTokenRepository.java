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
}
