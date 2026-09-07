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
     * "일괄 갱신" 표현과 일치) — clearAutomatically로 영속성 컨텍스트에 남아있을 수 있는 캐시된
     * RefreshToken 엔티티가 이 벌크 UPDATE 이후에도 이전 revokedYn 값을 들고 있지 않게 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedYn = true WHERE r.user.userId = :userId AND r.revokedYn = false")
    void revokeAllByUserId(@Param("userId") Long userId);
}
