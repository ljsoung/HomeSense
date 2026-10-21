package com.jiseong.homesense.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.jiseong.homesense.common.security.AccessTokenEpochService;
import com.jiseong.homesense.common.security.FailedAttemptPolicy;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.user.dto.WithdrawCommand;
import com.jiseong.homesense.user.exception.InvalidCurrentPasswordException;
import com.jiseong.homesense.user.exception.PasswordConfirmLockedException;
import com.jiseong.homesense.support.RedisContainerSupport;

/**
 * MY-05 비밀번호 변경(PUT /api/users/me)과 탈퇴(DELETE /api/users/me)의 비밀번호 확인(공용 실패 잠금)을 실제 필터 체인(MockMvc)·MariaDB·Redis로 검증한다. UserServiceTest(Mockito)는
 * 세션 종료 호출 여부만 증명하고, (1) 그 호출이 이 회원의 Refresh Token을 실제로 모두 폐기하고 기존 Access Token을
 * JwtAuthenticationFilter에서 401로 만드는지, (2) 실패하면 비밀번호·닉네임·토큰이 커밋되지 않는지는 증명하지 못한다.
 *
 * <p>Redis는 Testcontainers Redis({@link RedisContainerSupport} — 클래스마다 비운다)를 쓴다. 같은 클래스의 테스트끼리
 * 섞이지 않게 매 테스트 전후로 이 회원의 키를 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
class UserPasswordConfirmMariaDbIT extends RedisContainerSupport {

    private static final String EMAIL = "password-confirm-it@test.com";
    private static final String PASSWORD = "Abcd1234!";
    private static final String NEW_PASSWORD = "Efgh5678!";

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

    /** 실제 동작 그대로 쓰되, 원자성 테스트에서만 컷오프 쓰기가 실패하게 만든다. */
    @MockitoSpyBean
    private AccessTokenEpochService accessTokenEpochService;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    /** 동시 요청 테스트에서 실제로 비교까지 간 시도 수를 센다. 동작은 실제 그대로다. */
    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserService userService;

    private long userId;
    private String accessToken;

    @BeforeEach
    void setUp() {
        WithdrawalTestSeed seed = new WithdrawalTestSeed(jdbc);
        seed.wipeAll();
        userId = seed.user(EMAIL, passwordEncoder.encode(PASSWORD), "ACTIVE", null);
        redis.delete(List.of("user:status:" + userId, "user:tokenEpochMs:" + userId, "password-confirm:fail:" + userId,
                "login:fail:" + EMAIL));
        // 이 기기와 다른 기기의 세션 두 개. 이 IT는 폐기 여부만 세므로 저장값은 고유하기만 하면 된다.
        seed.refreshToken(userId, "password-confirm-it-device-a", false);
        seed.refreshToken(userId, "password-confirm-it-device-b", false);
        accessToken = jwtTokenProvider.createAccessToken(userId, "USER");
    }

    /** 남긴 잠금 카운트가 같은 클래스의 다음 테스트를 막지 않게 지운다(다른 IT 클래스와는 RedisContainerSupport가 클래스마다 비워 격리된다). */
    @AfterEach
    void cleanUpRedis() {
        redis.delete(List.of("user:status:" + userId, "user:tokenEpochMs:" + userId, "password-confirm:fail:" + userId,
                "login:fail:" + EMAIL));
    }

    @Test
    void 비밀번호를_바꾸면_닉네임과_함께_저장되고_Refresh_Token이_모두_폐기되고_기존_Access_Token은_401이다() throws Exception {
        expectMe(accessToken, 200);

        mockMvc.perform(put("/api/users/me").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("새닉네임", PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));

        assertThat(passwordEncoder.matches(NEW_PASSWORD, storedPassword())).isTrue();
        assertThat(storedNickname()).isEqualTo("새닉네임");
        assertThat(activeRefreshTokens()).isZero();
        expectMe(accessToken, 401);

        // 새 비밀번호로 로그인하면 정상 — 옛 비밀번호는 거부된다.
        login(PASSWORD, 401);
        expectMe(login(NEW_PASSWORD, 200), 200);
    }

    @Test
    void 현재_비밀번호가_틀리면_400이고_닉네임_비밀번호_토큰_모두_그대로이며_실패_카운트가_남는다() throws Exception {
        mockMvc.perform(put("/api/users/me").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("새닉네임", "Wrong123!", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CURRENT_PASSWORD"));

        assertThat(passwordEncoder.matches(PASSWORD, storedPassword())).isTrue();
        assertThat(storedNickname()).isEqualTo("닉네임");
        assertThat(activeRefreshTokens()).isEqualTo(2);
        assertThat(redis.opsForValue().get("password-confirm:fail:" + userId)).isEqualTo("1");
        expectMe(accessToken, 200);
    }

    @Test
    void 실패가_5회_쌓이면_맞는_비밀번호도_429로_막히고_아무것도_바뀌지_않는다() throws Exception {
        redis.opsForValue().set("password-confirm:fail:" + userId, "5");

        mockMvc.perform(put("/api/users/me").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CONFIRM_LOCKED"));

        assertThat(passwordEncoder.matches(PASSWORD, storedPassword())).isTrue();
        assertThat(activeRefreshTokens()).isEqualTo(2);
    }

    @Test
    void 컷오프_쓰기가_실패하면_비밀번호_닉네임_토큰_폐기가_모두_롤백된다() throws Exception {
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(accessTokenEpochService).invalidateTokensIssuedBefore(eq(userId), any());

        mockMvc.perform(put("/api/users/me").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("새닉네임", PASSWORD, NEW_PASSWORD)))
                .andExpect(status().is5xxServerError());

        assertThat(passwordEncoder.matches(PASSWORD, storedPassword())).isTrue();
        assertThat(storedNickname()).isEqualTo("닉네임");
        assertThat(activeRefreshTokens()).isEqualTo(2);
    }

    @Test
    void 탈퇴_비밀번호가_틀리면_400이고_탈퇴되지_않으며_공용_카운트가_남는다() throws Exception {
        withdraw("Wrong123!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CURRENT_PASSWORD"));

        assertThat(storedStatus()).isEqualTo("ACTIVE");
        assertThat(activeRefreshTokens()).isEqualTo(2);
        assertThat(failCount()).isEqualTo("1");
        expectMe(accessToken, 200);
    }

    @Test
    void 비밀번호_변경_실패와_탈퇴_실패가_합산돼_5회면_두_기능_모두_맞는_비밀번호도_429다() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(put("/api/users/me").header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(null, "Wrong123!", NEW_PASSWORD)))
                    .andExpect(status().isBadRequest());
        }
        for (int i = 0; i < 2; i++) {
            withdraw("Wrong123!").andExpect(status().isBadRequest());
        }
        assertThat(failCount()).isEqualTo("5");

        withdraw(PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CONFIRM_LOCKED"));
        mockMvc.perform(put("/api/users/me").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CONFIRM_LOCKED"));

        assertThat(storedStatus()).isEqualTo("ACTIVE");
        assertThat(passwordEncoder.matches(PASSWORD, storedPassword())).isTrue();
        assertThat(activeRefreshTokens()).isEqualTo(2);
    }

    /**
     * 성공해도 카운터를 지우지 않는다 — 성공한 시도도 센 채로 남고, 잠금은 TTL 만료로만 풀린다. 미리 심은 키에는 TTL이 없다 — 예약이
     * TTL 없는 키에 TTL(5분)을 걸어 영구 잠금이 생기지 않는다(AttemptLimiter).
     */
    @Test
    void 탈퇴에_성공해도_공용_카운트는_남고_TTL_없던_키도_TTL을_받는다() throws Exception {
        redis.opsForValue().set("password-confirm:fail:" + userId, "2");

        withdraw(PASSWORD).andExpect(status().isOk());

        assertThat(storedStatus()).isEqualTo("WITHDRAWN");
        assertThat(failCount()).isEqualTo("3");
        assertThat(redis.getExpire("password-confirm:fail:" + userId, TimeUnit.MILLISECONDS))
                .isPositive()
                .isLessThanOrEqualTo(FailedAttemptPolicy.LOCK_TTL.toMillis());
    }

    /**
     * 잠금 확인과 실패 기록이 따로였을 때는 BCrypt 비교 동안 동시 요청이 모두 잠금 확인을 통과해 8건 모두 비교됐다(전부 400).
     * 시도를 비교 전에 원자적으로 예약하면 정확히 5건만 비교되고 나머지는 429다. 메인 스레드는 조율·검증만 하고 서비스 호출은 워커
     * 스레드에서 한다(각자 트랜잭션·커넥션).
     */
    @Test
    void 같은_회원의_틀린_비밀번호_요청이_동시에_와도_비교까지_가는_시도는_5회를_넘지_않는다() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier start = new CyclicBarrier(threads);
        try {
            List<Future<Class<?>>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        userService.withdraw(userId, new WithdrawCommand("Wrong123!"));
                        return null;
                    } catch (InvalidCurrentPasswordException | PasswordConfirmLockedException e) {
                        return e.getClass();
                    }
                }));
            }
            List<Class<?>> outcomes = new ArrayList<>();
            for (Future<Class<?>> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(outcomes).filteredOn(InvalidCurrentPasswordException.class::equals).hasSize(5);
            assertThat(outcomes).filteredOn(PasswordConfirmLockedException.class::equals).hasSize(threads - 5);
            // 결과 개수만으로는 "비교한 뒤 예약"하는 회귀를 못 잡는다 — 잠긴 요청은 비교 자체를 하지 않아야 한다.
            verify(passwordEncoder, times(5)).matches(eq("Wrong123!"), any());
        } finally {
            pool.shutdownNow();
        }

        // 거부된 시도도 카운트에 남는다(공용 제한기 — 예전엔 잠긴 뒤 늘지 않았다). TTL은 첫 예약 때 건 것 그대로라 잠금이 연장되지 않는다.
        assertThat(failCount()).isEqualTo(String.valueOf(threads));
        assertThat(redis.getExpire("password-confirm:fail:" + userId, TimeUnit.MILLISECONDS))
                .isPositive()
                .isLessThanOrEqualTo(FailedAttemptPolicy.LOCK_TTL.toMillis());
        assertThat(storedStatus()).isEqualTo("ACTIVE");
    }

    /**
     * 맞는 비밀번호를 비교하는 동안(BCrypt) 같은 회원의 틀린 시도 4건이 예약·실패해도, 성공이 카운터를 지우지 않으므로 4건이 그대로
     * 남는다(성공한 시도까지 5) — 성공 시 초기화가 있던 때는 4건이 사라져 곧바로 5건을 더 비교할 수 있었다(코드리뷰 P2). 다음 시도는
     * 비교 없이 429이고, 잠금은 TTL이 끝나야 풀린다.
     */
    @Test
    void 맞는_비밀번호를_비교하는_동안_예약된_틀린_시도는_성공_뒤에도_남고_잠금은_TTL로만_풀린다() throws Exception {
        CountDownLatch comparing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            comparing.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("맞는 비밀번호 비교가 풀리지 않았다");
            }
            return invocation.callRealMethod();
        }).when(passwordEncoder).matches(eq(PASSWORD), any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> valid = pool.submit(() -> userService.withdraw(userId, new WithdrawCommand(PASSWORD)));
            assertThat(comparing.await(10, TimeUnit.SECONDS)).isTrue();

            for (int i = 0; i < 4; i++) {
                assertThat(pool.submit(wrongWithdraw()).get(30, TimeUnit.SECONDS))
                        .isEqualTo(InvalidCurrentPasswordException.class);
            }
            assertThat(failCount()).isEqualTo("5");

            release.countDown();
            valid.get(30, TimeUnit.SECONDS);

            // 성공해도 틀린 시도 4건(과 성공한 시도)은 그대로다 — 다음 시도는 비교하지 않고 잠긴다.
            assertThat(failCount()).isEqualTo("5");
            assertThat(pool.submit(wrongWithdraw()).get(30, TimeUnit.SECONDS))
                    .isEqualTo(PasswordConfirmLockedException.class);
            verify(passwordEncoder, times(4)).matches(eq("Wrong123!"), any());

            // TTL이 끝나면 풀린다(5분을 기다리는 대신 만료를 앞당긴다).
            String key = "password-confirm:fail:" + userId;
            redis.expire(key, Duration.ofMillis(1));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (Boolean.TRUE.equals(redis.hasKey(key)) && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(pool.submit(wrongWithdraw()).get(30, TimeUnit.SECONDS))
                    .isEqualTo(InvalidCurrentPasswordException.class);
            assertThat(failCount()).isEqualTo("1");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private Callable<Class<?>> wrongWithdraw() {
        return () -> {
            try {
                userService.withdraw(userId, new WithdrawCommand("Wrong123!"));
                return null;
            } catch (InvalidCurrentPasswordException | PasswordConfirmLockedException e) {
                return e.getClass();
            }
        };
    }

    private org.springframework.test.web.servlet.ResultActions withdraw(String password) throws Exception {
        return mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}"));
    }

    private String failCount() {
        return redis.opsForValue().get("password-confirm:fail:" + userId);
    }

    private String storedStatus() {
        return jdbc.queryForObject("SELECT status FROM user WHERE user_id = ?", String.class, userId);
    }

    private static String body(String nickname, String currentPassword, String newPassword) {
        StringBuilder json = new StringBuilder("{");
        if (nickname != null) json.append("\"nickname\":\"").append(nickname).append("\",");
        json.append("\"currentPassword\":\"").append(currentPassword).append("\",");
        json.append("\"newPassword\":\"").append(newPassword).append("\"}");
        return json.toString();
    }

    private String storedPassword() {
        return jdbc.queryForObject("SELECT password FROM user WHERE user_id = ?", String.class, userId);
    }

    private String storedNickname() {
        return jdbc.queryForObject("SELECT nickname FROM user WHERE user_id = ?", String.class, userId);
    }

    private long activeRefreshTokens() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_yn = FALSE",
                Long.class, userId);
        return count == null ? 0 : count;
    }

    private void expectMe(String token, int expectedStatus) throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }

    private String login(String password, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return expectedStatus == 200 ? JsonPath.read(response, "$.data.accessToken") : null;
    }
}
