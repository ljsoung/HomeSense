package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.auth.dto.LoginCommand;
import com.jiseong.homesense.auth.exception.AccountLockedException;
import com.jiseong.homesense.common.exception.InvalidCredentialsException;
import com.jiseong.homesense.common.security.AttemptLimiter;
import com.jiseong.homesense.common.security.FailedAttemptPolicy;
import com.jiseong.homesense.support.RedisContainerSupport;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

/**
 * SVC-AUTH-01 로그인 실패 잠금을 실제 Redis(Testcontainers)·MariaDB로 검증한다. AuthServiceTest(Mockito)는 호출 순서만 증명하고,
 * (1) 동시 요청에서 비밀번호 평가가 정말 5회를 넘지 않는지, (2) Lua 예약이 TTL 없는 키를 복구하는지, (3) 되돌리기가 다른 요청의
 * 예약을 지우지 않는지는 실제 Redis에서만 드러난다.
 *
 * <p>평가 횟수는 PasswordEncoder 스파이로 센다(동작은 실제 BCrypt 그대로). 서비스 호출은 워커 스레드에서 한다(각자 트랜잭션).
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class AuthServiceLoginAttemptMariaDbIT extends RedisContainerSupport {

    private static final String EMAIL = "login-attempt-it@test.com";
    private static final String PASSWORD = "Abcd1234!";
    private static final String WRONG = "Wrong123!";
    private static final String KEY = "login:fail:" + EMAIL;

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
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private AttemptLimiter attemptLimiter;
    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM user");
        userRepository.saveAndFlush(User.createUser(EMAIL, passwordEncoder.encode(PASSWORD), "닉네임"));
        redis.delete(List.of(KEY, "login:fail:nobody@test.com", "limiter-it"));
        clearInvocations(passwordEncoder);
    }

    /**
     * 예전(잠금 확인 → BCrypt 비교 → 실패 기록)에는 BCrypt 비교 동안 동시 요청이 모두 잠금 확인을 통과해 20건이 모두 비교됐다.
     * 비교 전에 원자적으로 예약하면 정확히 5건만 비교되고 나머지는 429다.
     */
    @Test
    void 같은_이메일로_틀린_비밀번호_로그인이_동시에_20건_와도_비밀번호_평가는_정확히_5회다() throws Exception {
        List<Class<?>> outcomes = loginConcurrently(EMAIL, 20);

        assertThat(outcomes).filteredOn(InvalidCredentialsException.class::equals).hasSize(5);
        assertThat(outcomes).filteredOn(AccountLockedException.class::equals).hasSize(15);
        verify(passwordEncoder, times(5)).matches(eq(WRONG), any());
        // 거부된 시도도 세지만 TTL은 첫 예약 때 건 것 그대로다(잠금 기간이 늘지 않는다).
        assertThat(redis.opsForValue().get(KEY)).isEqualTo("20");
        assertThat(redis.getExpire(KEY, TimeUnit.MILLISECONDS)).isPositive()
                .isLessThanOrEqualTo(FailedAttemptPolicy.LOCK_TTL.toMillis());
    }

    /** 없는 이메일도 같은 규칙으로 잠기고(가입 여부 비노출), 잠기기 전까지는 더미 해시와 한 번씩 비교한다(응답 시간 맞춤). */
    @Test
    void 없는_이메일도_동시_요청에서_5회까지만_비교하고_나머지는_429다() throws Exception {
        List<Class<?>> outcomes = loginConcurrently("nobody@test.com", 20);

        assertThat(outcomes).filteredOn(InvalidCredentialsException.class::equals).hasSize(5);
        assertThat(outcomes).filteredOn(AccountLockedException.class::equals).hasSize(15);
        verify(passwordEncoder, times(5)).matches(eq(WRONG), any());
    }

    /** 옛 코드가 INCR 뒤 EXPIRE 전에 실패해 남긴 키 — 다음 예약이 TTL을 걸어 영구 잠금이 풀린다. */
    @Test
    void TTL_없는_잠금_키가_있어도_다음_예약에서_TTL을_받는다() {
        redis.opsForValue().set(KEY, "7");
        assertThat(redis.getExpire(KEY)).isEqualTo(-1L);

        assertThatLocked();

        assertThat(redis.opsForValue().get(KEY)).isEqualTo("8");
        assertThat(redis.getExpire(KEY, TimeUnit.MILLISECONDS)).isPositive()
                .isLessThanOrEqualTo(FailedAttemptPolicy.LOCK_TTL.toMillis());
    }

    /** 성공은 자기 예약 하나만 되돌린다 — 앞선 실패 3건은 TTL까지 남는다(키를 지우지 않는다). */
    @Test
    void 로그인에_성공하면_자기_예약만_되돌리고_이전_실패는_남는다() {
        for (int i = 0; i < 3; i++) {
            loginExpecting(WRONG, InvalidCredentialsException.class);
        }
        long ttlBefore = redis.getExpire(KEY, TimeUnit.MILLISECONDS);

        authService.login(new LoginCommand(EMAIL, PASSWORD));

        assertThat(redis.opsForValue().get(KEY)).isEqualTo("3");
        assertThat(redis.getExpire(KEY, TimeUnit.MILLISECONDS)).isPositive().isLessThanOrEqualTo(ttlBefore);
        // 실패 2건을 더 하면 5건으로 잠긴다(성공이 카운터를 초기화하지 않았다).
        loginExpecting(WRONG, InvalidCredentialsException.class);
        loginExpecting(WRONG, InvalidCredentialsException.class);
        assertThatLocked();
    }

    @Test
    void 되돌리기는_0_아래로_내려가지_않고_없는_키를_만들지_않는다() {
        attemptLimiter.release("limiter-it");
        assertThat(redis.hasKey("limiter-it")).isFalse();

        redis.opsForValue().set("limiter-it", "0");
        attemptLimiter.release("limiter-it");
        assertThat(redis.opsForValue().get("limiter-it")).isEqualTo("0");
    }

    /** 맞는 비밀번호도 429이고 비밀번호를 평가하지 않는다. */
    private void assertThatLocked() {
        clearInvocations(passwordEncoder);
        loginExpecting(PASSWORD, AccountLockedException.class);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    private void loginExpecting(String password, Class<? extends RuntimeException> expected) {
        try {
            authService.login(new LoginCommand(EMAIL, password));
            throw new AssertionError("로그인이 성공하면 안 된다: " + expected.getSimpleName() + " 기대");
        } catch (RuntimeException e) {
            assertThat(e).isInstanceOf(expected);
        }
    }

    private List<Class<?>> loginConcurrently(String email, int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier start = new CyclicBarrier(threads);
        try {
            List<Future<Class<?>>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        authService.login(new LoginCommand(email, WRONG));
                        return null;
                    } catch (InvalidCredentialsException | AccountLockedException e) {
                        return e.getClass();
                    }
                }));
            }
            List<Class<?>> outcomes = new ArrayList<>();
            for (Future<Class<?>> result : results) {
                outcomes.add(result.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
