package com.jiseong.homesense.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.auth.entity.RefreshToken;
import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.common.security.UserStatusCacheService;
import com.jiseong.homesense.user.dto.WithdrawCommand;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.entity.UserStatus;
import com.jiseong.homesense.user.repository.UserRepository;
import com.jiseong.homesense.support.RedisContainerSupport;

/**
 * UserServiceTest의 withdraw() 성공 테스트는 UserRepository/RefreshTokenRepository를 전부 Mockito로
 * 목킹해 "user.withdraw()가 Java 필드를 바꿨다"와 "revokeAllByUserId(userId)가 호출됐다"만 증명한다 —
 * 실제 EntityManager/영속성 컨텍스트가 전혀 없으니 그 변경이 진짜 DB에 반영되는지는 애초에 검증
 * 대상이 아니다.
 *
 * <p>그런데 실제로는 반영되지 않을 수 있었다(코드리뷰에서 지적됨) — RefreshTokenRepository.
 * revokeAllByUserId()의 JPQL 벌크 UPDATE는 query space가 refresh_token뿐이라(user와 join하지
 * 않는다) 그 직전 UserService.withdraw()가 만든 User의 미반영 변경(status=WITHDRAWN)을 Hibernate의
 * 자동 flush-before-query가 감지하지 못했고, 그 상태에서 clearAutomatically=true만 걸려 있으면 그
 * 벌크 UPDATE 직후 영속성 컨텍스트가 clear되며 flush된 적 없는 User의 변경이 통째로 버려졌다 —
 * refresh_token은 정상적으로 revoked_yn=true가 되는데 user.status는 ACTIVE로 남아, Mockito
 * 단위 테스트로는 절대 드러나지 않는 결함이었다.
 *
 * <p>이 테스트는 실제 MariaDB(Testcontainers) 위에서 UserService.withdraw() 트랜잭션이 끝난 뒤 커밋된
 * 실제 DB 상태를 재조회해, User.status/withdrawnAt과 RefreshToken.revokedYn이 둘 다 실제로 반영됐는지
 * 함께 검증한다.
 *
 * <p>Docker가 필요해 기본 `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class UserServiceMariaDbIT extends RedisContainerSupport {

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:10.11")
            .withDatabaseName("homesense_it")
            .withUsername("homesense")
            .withPassword("homesense");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", MARIADB::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/user-withdraw-schema.sql");
    }

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redis;
    @MockitoSpyBean
    private UserStatusCacheService userStatusCacheService;

    @Test
    void 탈퇴하면_커밋_이후_재조회에서도_회원_상태와_토큰_폐기가_모두_반영돼_있다() {
        User user = userRepository.saveAndFlush(
                User.createUser("withdraw-it@test.com", passwordEncoder.encode("Abcd1234!"), "닉네임"));
        RefreshToken token = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(user, "token-hash", LocalDateTime.now().plusDays(1)));

        // 탈퇴도 공용 비밀번호 확인 잠금(password-confirm:fail:{userId})을 본다. Redis는 클래스마다 비워지지만(RedisContainerSupport)
        // 같은 클래스의 다른 테스트가 같은 userId로 카운트를 남겼을 수 있어 먼저 지운다.
        redis.delete("password-confirm:fail:" + user.getUserId());
        userService.withdraw(user.getUserId(), new WithdrawCommand("Abcd1234!"));

        // userService.withdraw()가 반환된 시점엔 이미 트랜잭션이 커밋된 뒤다(프록시 경계) — 여기서
        // 다시 조회하는 User/RefreshToken은 withdraw() 호출 중 쓰던 것과 같은 영속성 컨텍스트가
        // 아니라 새로 읽어온 값이라, "메모리상 객체가 바뀐 것처럼 보이는" 착시 없이 실제 커밋된
        // DB 상태만 확인한다.
        User reloadedUser = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(reloadedUser.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(reloadedUser.getWithdrawnAt()).isNotNull();

        RefreshToken reloadedToken = refreshTokenRepository.findById(token.getRefreshTokenId()).orElseThrow();
        assertThat(reloadedToken.isUsable()).isFalse();
    }

    /**
     * WITHDRAWN 캐시 쓰기는 탈퇴 트랜잭션 안(커밋 전)에 있다. 그래서 그 쓰기가 Redis 장애로 실패하면 탈퇴 전체가 롤백돼야 한다 —
     * DB에서는 탈퇴됐는데 사용자는 503을 받는 상태가 생기지 않는다(CLAUDE.md "Redis 장애 시 강등과 막힌 쪽 실패" 절 분류표).
     * revokeAllByUserId()의 flushAutomatically가 User 변경을 먼저 flush하지만 커밋 전이라 함께 롤백된다.
     */
    @Test
    void WITHDRAWN_캐시_쓰기가_Redis_장애로_실패하면_탈퇴와_토큰_폐기가_모두_롤백된다() {
        User user = userRepository.saveAndFlush(
                User.createUser("withdraw-redis-down@test.com", passwordEncoder.encode("Abcd1234!"), "닉네임"));
        RefreshToken token = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(user, "token-hash-redis-down", LocalDateTime.now().plusDays(1)));
        redis.delete("password-confirm:fail:" + user.getUserId());
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(userStatusCacheService).setStatus(eq(user.getUserId()), any());

        assertThatThrownBy(() -> userService.withdraw(user.getUserId(), new WithdrawCommand("Abcd1234!")))
                .isInstanceOf(RedisConnectionFailureException.class);

        User reloadedUser = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(reloadedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(reloadedUser.getWithdrawnAt()).isNull();
        assertThat(refreshTokenRepository.findById(token.getRefreshTokenId()).orElseThrow().isUsable()).isTrue();
        assertThat(redis.hasKey("user:status:" + user.getUserId())).isFalse();
    }
}
