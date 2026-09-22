package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.auth.entity.RefreshToken;
import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.common.security.AccessTokenEpochService;
import com.jiseong.homesense.common.security.JwtAuthenticationFilter;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

/**
 * {@code AuthServiceTest.resetPassword_성공하면_...}은 UserRepository/RefreshTokenRepository를 전부
 * Mockito로 목킹해 "user.changePassword()가 Java 필드를 바꿨다"와 "revokeAllByUserId(userId)가 호출됐다"만
 * 증명한다 — {@code UserServiceMariaDbIT}가 {@code withdraw()}에서 이미 검증한 것과 똑같은 함정이
 * 구조적으로 여기도 그대로 있다: {@code AuthService.resetPassword()}도 {@code user.changePassword()}
 * (dirty) 직후 곧바로 {@code refreshTokenRepository.revokeAllByUserId()}(query space가 refresh_token
 * 뿐인 벌크 UPDATE)를 호출하는 같은 순서다. 그 메서드의 {@code flushAutomatically=true}가 이 순서를
 * 안전하게 만들어 주는 것은 맞지만, 이 보장 자체가 실제 DB에서 성립하는지는 Mockito로 증명할 수 없다.
 *
 * <p>이 IT는 실제 MariaDB(Testcontainers) 위에서 resetPassword() 트랜잭션이 끝난 뒤 커밋된 실제 DB
 * 상태를 재조회해, User.password와 RefreshToken.revokedYn이 둘 다 실제로 반영됐는지, 그리고 재설정
 * 토큰이 1회성으로 소비돼 재사용이 불가능한지 함께 검증한다. 스키마는 UserServiceMariaDbIT와 동일한
 * user/refresh_token 두 테이블만 필요해 그 IT의 fixture(user-withdraw-schema.sql)를 그대로 재사용한다.
 *
 * <p>Redis 연결은 LoginAttemptService/UserStatusCacheService가 이미 요구하던 인프라라 이 IT로 새로
 * 추가되는 테스트 인프라 요구사항은 없다(CLAUDE.md COM-SEC-01/02 절 참고) — {@link PasswordResetTokenService}도
 * 같은 로컬 Redis(localhost:6379)를 그대로 쓴다.
 *
 * <p>Docker가 필요해 기본 `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class AuthServicePasswordResetMariaDbIT {

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
    private AuthService authService;
    @Autowired
    private PasswordResetTokenService passwordResetTokenService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private AccessTokenEpochService accessTokenEpochService;

    @Test
    void 재설정하면_커밋_이후_재조회에서도_새_비밀번호와_토큰_전체_폐기가_모두_반영돼_있고_토큰은_1회성이다() {
        User user = userRepository.saveAndFlush(
                User.createUser("reset-it@test.com", passwordEncoder.encode("OldAbcd1234!"), "닉네임"));
        RefreshToken token = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(user, "token-hash", LocalDateTime.now().plusDays(1)));
        String rawToken = passwordResetTokenService.issueToken(user.getUserId());

        authService.resetPassword(rawToken, "NewAbcd1234!");

        // authService.resetPassword()가 반환된 시점엔 이미 트랜잭션이 커밋된 뒤다(프록시 경계) —
        // 여기서 다시 조회하는 User/RefreshToken은 resetPassword() 호출 중 쓰던 것과 같은 영속성
        // 컨텍스트가 아니라 새로 읽어온 값이라, 실제 커밋된 DB 상태만 확인한다.
        User reloadedUser = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewAbcd1234!", reloadedUser.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("OldAbcd1234!", reloadedUser.getPassword())).isFalse();

        RefreshToken reloadedToken = refreshTokenRepository.findById(token.getRefreshTokenId()).orElseThrow();
        assertThat(reloadedToken.isUsable()).isFalse();

        // 토큰은 GETDEL로 이미 소비됐다 — 같은 토큰으로 다시 consume을 시도하면 빈 Optional이어야 한다.
        assertThat(passwordResetTokenService.consumeToken(rawToken)).isEmpty();
    }

    @Test
    void 같은_사용자가_재설정을_다시_요청하면_이전_토큰은_새_토큰_발급과_동시에_무효화된다() {
        User user = userRepository.saveAndFlush(
                User.createUser("reissue-it@test.com", passwordEncoder.encode("OldAbcd1234!"), "닉네임"));

        String firstRawToken = passwordResetTokenService.issueToken(user.getUserId());
        String secondRawToken = passwordResetTokenService.issueToken(user.getUserId());

        // 오래된 이메일 링크(첫 번째 토큰)는 두 번째 발급과 동시에 죽어야 한다 — 실제 Redis에서
        // issueToken()의 invalidatePreviousToken()이 정확히 동작하는지 확인한다(Mockito로는 이
        // 무효화 자체가 실제로 일어나는지 증명할 수 없다).
        assertThat(passwordResetTokenService.peekToken(firstRawToken)).isEmpty();
        assertThat(passwordResetTokenService.peekToken(secondRawToken)).contains(user.getUserId());

        authService.resetPassword(secondRawToken, "NewAbcd1234!");

        User reloadedUser = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewAbcd1234!", reloadedUser.getPassword())).isTrue();
    }

    /**
     * 코드리뷰 P1 지적 — Refresh Token 폐기만으로는 재설정 이전에 이미 발급된 Access Token을 막지
     * 못한다({@code AuthService.resetPassword()} javadoc 참고). 이 IT는 실제 Redis 위에서
     * {@link JwtAuthenticationFilter}가 매 요청 수행하는 것과 정확히 같은 두 호출
     * (userStatusResolver.isActive() 상당 + accessTokenEpochService.isIssuedAfterCutoff())을 그대로
     * 재현해, resetPassword() 이전에 발급된 Access Token이 그 이후에는 걸러지고 이후에 발급된
     * 새 Access Token은 그대로 통과하는지 확인한다 — Mockito로는 "실제 Redis에 컷오프가 기록되고
     * 그 값을 다시 읽어 정확히 비교되는지" 자체를 증명할 수 없다.
     *
     * <p>{@code Thread.sleep(1100)}는 타이밍을 피해 가는 임시방편이 아니라 이 테스트가 검증하려는
     * 것 자체의 전제조건이다 — JWT {@code iat}(NumericDate)는 초 단위로 잘리므로(COM-SEC-02
     * {@code jti} 도입 배경과 같은 특성), stale 토큰과 컷오프가 같은 초 안에서 발급되면(디스크·네트워크
     * 지연이 없는 이 테스트 환경에서는 그러기가 오히려 쉽다) {@link AccessTokenEpochService}가 의도적으로
     * "같은 초는 통과시킨다"는 트레이드오프를 택하고 있어(그 클래스 javadoc 참고, 실사용자 로그인을
     * 최대 30분 막는 반대 방향 오탐을 피하기 위함) stale 토큰이 걸러지지 않는 게 오히려 설계대로다.
     * 최초 이 테스트를 sleep 없이 작성했다가 정확히 이 이유로 실패해서(실 Redis 위에서 실제로
     * 재현됨) sleep을 추가했다 — 실 운영에서는 재설정 요청과 그 이전 로그인 사이에 최소 수 초~수 분이
     * 있어 이 창이 사실상 문제되지 않는다.
     */
    @Test
    void 재설정_이전에_발급된_AccessToken은_재설정_이후_컷오프에_걸리고_이후에_발급된_토큰은_통과한다() throws InterruptedException {
        User user = userRepository.saveAndFlush(
                User.createUser("epoch-it@test.com", passwordEncoder.encode("OldAbcd1234!"), "닉네임"));
        Long userId = user.getUserId();
        String rawToken = passwordResetTokenService.issueToken(userId);

        String staleAccessToken = jwtTokenProvider.createAccessToken(userId, "USER");
        Instant staleIssuedAt = jwtTokenProvider.getIssuedAt(staleAccessToken);

        Thread.sleep(1100);
        authService.resetPassword(rawToken, "NewAbcd1234!");

        String freshAccessToken = jwtTokenProvider.createAccessToken(userId, "USER");
        Instant freshIssuedAt = jwtTokenProvider.getIssuedAt(freshAccessToken);

        assertThat(accessTokenEpochService.isIssuedAfterCutoff(userId, staleIssuedAt)).isFalse();
        assertThat(accessTokenEpochService.isIssuedAfterCutoff(userId, freshIssuedAt)).isTrue();
    }
}
