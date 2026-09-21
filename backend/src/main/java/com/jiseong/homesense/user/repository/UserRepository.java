package com.jiseong.homesense.user.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jiseong.homesense.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일 대소문자 구분 없이 조회한다. 저장 시 소문자로 정규화되므로
     * 조회 시에도 {@link User#normalizeEmail}로 동일하게 정규화해야 매칭된다.
     */
    default Optional<User> findByEmail(String email) {
        return findByNormalizedEmail(User.normalizeEmail(email));
    }

    default boolean existsByEmail(String email) {
        return existsByNormalizedEmail(User.normalizeEmail(email));
    }

    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByNormalizedEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = :email")
    boolean existsByNormalizedEmail(@Param("email") String email);

    /**
     * BAT-USR-01 — 파기 대상(탈퇴 후 유예기간이 지난) 회원 ID를 user_id 오름차순 keyset으로 조회한다.
     * {@code afterId}보다 큰 ID만 읽으므로, 파기에 실패한 행이 있어도 호출자가 커서를 앞으로만 옮기면
     * 반복이 반드시 끝난다. {@code withdrawn_at IS NULL}인 WITHDRAWN 행은 {@code <=} 비교에서 빠져
     * 대상이 되지 않는다(삭제 안전장치 — {@link #countWithdrawnWithoutTimestamp()}로 별도 집계).
     */
    @Query("""
            SELECT u.userId FROM User u
            WHERE u.status = com.jiseong.homesense.user.entity.UserStatus.WITHDRAWN
              AND u.withdrawnAt <= :threshold
              AND u.userId > :afterId
            ORDER BY u.userId
            """)
    List<Long> findPurgeTargetIds(@Param("threshold") LocalDateTime threshold, @Param("afterId") Long afterId,
            Pageable pageable);

    /**
     * BAT-USR-01 — 조건부 DELETE 1문. 자식 테이블(refresh_token, favorite_property, favorite_region, recent_view, notification_setting, notification)은 DB의
     * ON DELETE CASCADE가 지운다. 조건이 파기 대상 정의({@code withdrawn_at <= threshold})와 같고 status를
     * 함께 확인하므로, 조회 이후 그 사이 철회돼 ACTIVE가 됐다면 0을 반환한다(affected rows로 경합 판정).
     *
     * <p>{@code flushAutomatically}/{@code clearAutomatically}는 CLAUDE.md "@Modifying 벌크 쿼리" 원칙에 따른다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM User u
            WHERE u.userId = :id
              AND u.status = com.jiseong.homesense.user.entity.UserStatus.WITHDRAWN
              AND u.withdrawnAt <= :threshold
            """)
    int deleteIfPurgeable(@Param("id") Long id, @Param("threshold") LocalDateTime threshold);

    /**
     * SVC-AUTH-01.reactivate() — 유예기간 안({@code withdrawn_at > threshold})의 탈퇴 계정만 ACTIVE로 되돌린다.
     * {@link #deleteIfPurgeable}와 조건이 정확히 반대라 같은 행에 둘이 동시에 성립할 수 없다 — 경합하면
     * 먼저 커밋한 쪽이 이기고 나중 쪽은 affected rows가 0이 된다.
     *
     * <p>JPQL 벌크 UPDATE는 Spring Data auditing({@code @LastModifiedDate})을 우회하므로 {@code updatedAt}을
     * 직접 세팅한다. 이 메서드는 refresh_token을 건드리지 않는다 — 탈퇴 시 폐기된 토큰은 철회 후에도
     * 폐기 상태로 남고, 로그인 토큰은 새로 발급한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE User u
            SET u.status = com.jiseong.homesense.user.entity.UserStatus.ACTIVE,
                u.withdrawnAt = NULL,
                u.updatedAt = :now
            WHERE u.userId = :id
              AND u.status = com.jiseong.homesense.user.entity.UserStatus.WITHDRAWN
              AND u.withdrawnAt > :threshold
            """)
    int reactivateIfWithinGrace(@Param("id") Long id, @Param("threshold") LocalDateTime threshold,
            @Param("now") LocalDateTime now);

    /** status=WITHDRAWN인데 withdrawn_at이 NULL인 불일치 행 수 — 파기 대상에서 제외되므로 WARN 로그용으로만 센다. */
    @Query("""
            SELECT COUNT(u) FROM User u
            WHERE u.status = com.jiseong.homesense.user.entity.UserStatus.WITHDRAWN
              AND u.withdrawnAt IS NULL
            """)
    long countWithdrawnWithoutTimestamp();
}
