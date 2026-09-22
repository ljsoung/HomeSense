package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.auth.dto.TokenResponse;
import com.jiseong.homesense.auth.exception.InvalidRefreshTokenException;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.user.service.WithdrawalTestSeed;

/**
 * SVC-AUTH-01.refreshAccessToken() Rotation의 동시성 전제와, Rotation+logout()이 얽히는 재사용 탐지
 * 시나리오를 실제 MariaDB 위에서 검증한다.
 *
 * <p><b>동시성 전제(첫 번째 테스트).</b> {@code AuthServiceTest}(Mockito)는 {@code revokeIfUnrevoked()}의
 * 반환값을 직접 목킹해 "0이면 재사용 탐지로 분기한다"까지만 증명한다 — 그 전제, 즉 "같은 Refresh
 * Token으로 두 요청이 실제로 동시에 재발급을 시도하면 정확히 하나만 1을, 다른 하나는 0을 받는다"는
 * 실제 DB의 행 잠금 없이는 증명할 수 없다. {@code TradeChunkLoaderMariaDbIT}/{@code AuthServiceMariaDbIT}와
 * 달리 이 레이스는 별도의 {@code CountDownLatch} 오케스트레이션이 필요 없다 — {@code revokeIfUnrevoked()}의
 * UPDATE는 InnoDB에서 "current read"(잠금 읽기)라 REPEATABLE READ 스냅샷을 우회하고 항상 최신 커밋
 * 상태를 본다: 먼저 도착한 스레드가 그 행의 배타 잠금을 잡고 성공(1)하며, 늦게 도착한 스레드는 그
 * 잠금이 풀릴 때까지(=먼저 도착한 트랜잭션이 커밋할 때까지) 자연히 블록됐다가, 풀린 뒤 재평가한
 * WHERE절이 이미 {@code revoked_yn=true}로 바뀐 걸 보고 0을 받는다 — 두 스레드를 그냥 동시에
 * 제출하기만 하면 DB가 순서를 직렬화해 준다.
 *
 * <p><b>logout()의 재사용 탐지(두 번째 테스트).</b> {@code rotated_yn}이 실제 DB 컬럼에 원자적으로
 * 반영되고, {@code AuthService.logout()}이 그 값을 정확히 읽어 분기하는지는 컬럼 매핑·JPQL 오타 같은
 * 실수가 Mockito로는 절대 드러나지 않는 종류라(설정이 실제로 다 맞아야만 통과하는 회귀 테스트)
 * 실 DB로 끝까지 태운다 — 공격자 역할로 실제 rotation을 한 번 호출해 후속 토큰을 만든 뒤, 정상
 * 사용자 역할로 원래(이미 rotation된) 토큰으로 logout()을 호출해 그 후속 토큰까지 폐기되는지 확인한다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class AuthServiceRefreshRotationMariaDbIT {

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
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/withdrawn-user-purge-schema.sql");
    }

    @MockitoBean
    private AuditLogger auditLogger;

    @Autowired
    private AuthService authService;
    @Autowired
    private RefreshTokenHasher refreshTokenHasher;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private JdbcTemplate jdbc;

    private WithdrawalTestSeed seed;

    @BeforeEach
    void setUp() {
        seed = new WithdrawalTestSeed(jdbc);
        seed.wipeAll();
    }

    @Test
    void 같은_Refresh_Token으로_두_재발급_요청이_동시에_경쟁하면_하나만_성공하고_패자는_재사용_탐지로_처리된다() throws Exception {
        long userId = seed.user("race@test.com", "unused-encoded-password", "ACTIVE", null);
        // validateToken()/isAccessToken() 검사를 통과하려면 실제 JwtTokenProvider가 서명한 토큰이어야
        // 한다 — 임의 문자열은 파싱 단계에서 곧바로 InvalidRefreshTokenException으로 막힌다.
        String rawToken = jwtTokenProvider.createRefreshToken(userId);
        seed.refreshToken(userId, refreshTokenHasher.hash(rawToken), false);
        // JWT의 iat/exp 클레임(NumericDate, RFC 7519 §2)은 초 단위로 잘린다 — 밀리초가 아니다. 같은
        // 사용자에 대해 같은 "초" 안에 두 번 서명하면(여기서 시드한 토큰과 잠시 뒤 승자가 회전으로
        // 새로 발급하는 토큰) iat/exp가 초 단위로 완전히 같아져 헤더+페이로드+서명까지 토큰 문자열
        // 자체가 바이트 단위로 동일해진다 — token_value UNIQUE 제약 위반으로 이어진다(실제로 재현됨,
        // 처음엔 밀리초 단위 충돌로 오판해 10ms/100ms를 시도했으나 둘 다 불충분했다 — 원인은 시계
        // 해상도가 아니라 JWT NumericDate의 초 단위 절삭이었다). 실제 운영 환경에서는 로그인과 재발급
        // 사이에 최소 수 초~수 분이 지나 이 충돌이 성립하지 않지만, 이 테스트는 둘을 같은 메서드
        // 안에서 곧바로 이어 붙이므로 최소 1초 경계를 넘도록 여유를 둔다.
        Thread.sleep(1100);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<TokenResponse>> futures = List.of(
                    executor.submit(() -> authService.refreshAccessToken(rawToken)),
                    executor.submit(() -> authService.refreshAccessToken(rawToken)));

            int succeeded = 0;
            int rejected = 0;
            for (Future<TokenResponse> future : futures) {
                try {
                    TokenResponse response = future.get(10, TimeUnit.SECONDS);
                    assertThat(response.accessToken()).isNotBlank();
                    assertThat(response.refreshToken()).isNotBlank();
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(InvalidRefreshTokenException.class);
                    rejected++;
                }
            }

            // 정확히 하나만 회전에 성공한다 — 둘 다 성공(중복 회전)하거나 둘 다 실패(정상 요청 유실)
            // 하면 revokeIfUnrevoked()의 affected-rows 판정이 깨진 것이다.
            assertThat(succeeded).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
            // 패자는 재사용 탐지로 분기해 이 사용자의 Refresh Token을 전부 폐기한다 — 승자가 방금
            // 발급한 새 토큰까지 포함해서(패자의 revokeAllByUserId()는 승자의 커밋 이후에 실행되므로
            // 그 시점엔 새 토큰도 "아직 폐기되지 않은" 대상으로 보인다). 승자의 API 호출 자체는
            // 성공해 200을 돌려받았더라도, 그 토큰으로 다시 재발급을 시도하면 즉시 막히는 것이
            // 이 기능이 의도한 "애매한 동시 사용은 전부 무효화한다"는 보수적 동작이다.
            assertThat(seed.count("SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_yn = FALSE", userId))
                    .isZero();
            assertThat(seed.count("SELECT COUNT(*) FROM refresh_token WHERE user_id = ?", userId)).isEqualTo(2);
            verify(auditLogger).logRefreshTokenReuseDetected(userId);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 코드리뷰 P1 지적 — 공격자가 탈취한 토큰으로 먼저 rotation해 후속 토큰(공격자 세션)을 쥔 뒤,
     * 정상 사용자가 나중에 원래(이미 rotation된) 토큰으로 로그아웃을 시도하면, 예전 코드는 이를 그냥
     * "이미 폐기된 토큰을 다시 폐기하는 것"으로만 보고 조용히 성공 처리해 공격자의 후속 토큰을 전혀
     * 건드리지 않았다.
     */
    @Test
    void 이미_rotation된_토큰으로_로그아웃하면_후속_토큰까지_포함해_전부_폐기된다() throws Exception {
        long userId = seed.user("victim@test.com", "unused-encoded-password", "ACTIVE", null);
        String originalToken = jwtTokenProvider.createRefreshToken(userId);
        seed.refreshToken(userId, refreshTokenHasher.hash(originalToken), false);
        Thread.sleep(1100); // 위 첫 테스트와 같은 이유(JWT NumericDate 초 단위 절삭) — 시드 토큰과
                             // rotation이 발급하는 후속 토큰이 같은 초에 서명되지 않게 한다.

        // 공격자가 탈취한 originalToken으로 먼저 회전해 후속 토큰(공격자 세션)을 확보한다.
        authService.refreshAccessToken(originalToken);
        assertThat(seed.count("SELECT COUNT(*) FROM refresh_token WHERE user_id = ?", userId)).isEqualTo(2);

        // 정상 사용자가 (공격자의 rotation을 모른 채) 원래 토큰으로 로그아웃을 시도한다 — 예외 없이
        // 정상 종료돼야 한다(사용자 입장에선 "로그아웃"이 실패해 보이면 안 된다).
        authService.logout(userId, originalToken);

        // 공격자의 후속 토큰까지 포함해 이 사용자의 Refresh Token이 전부 폐기됐어야 한다.
        assertThat(seed.count("SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_yn = FALSE", userId))
                .isZero();
        verify(auditLogger).logRefreshTokenReuseDetected(userId);
    }
}
