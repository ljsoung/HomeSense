package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.auth.dto.TokenResponse;
import com.jiseong.homesense.auth.exception.InvalidRefreshTokenException;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.user.service.WithdrawalTestSeed;
import com.jayway.jsonpath.JsonPath;

/**
 * 재사용 탐지 시 이미 발급된 Access Token도 즉시 무효화되는지를 실제 필터 체인(MockMvc)·MariaDB·Redis로
 * 검증한다(2026-09-29). {@code RefreshTokenReuseHandlerTest}(Mockito)는 컷오프 호출 여부만 증명하고, 그
 * 컷오프가 {@code JwtAuthenticationFilter}에서 실제로 401을 만드는지는 증명하지 못한다.
 *
 * <p>시나리오: 공격자가 탈취한 Refresh Token으로 먼저 회전해 Access Token을 받는다. 정상 사용자가 원래
 * 토큰으로 재발급(또는 로그아웃)하면 재사용 탐지가 발동한다. 그 뒤 공격자의 Access Token과 정상 사용자가
 * 쓰던 Access Token 모두 인증이 필요한 API에서 401이어야 하고, 새로 로그인하면 정상 동작해야 한다.
 *
 * <p>공격자 회전과 탐지 사이에 대기를 두지 않는다 — 실제 공격에서 둘은 수 밀리초 안에 연달아 일어나므로
 * 같은 초 안에서도 막혀야 한다. 처음엔 컷오프를 초 단위({@code iat >= cutoff})로 비교해 이 경우가 통과했고,
 * 이 IT는 {@code Thread.sleep(1100)}으로 초 경계를 넘겨 그 결함을 가리고 있었다(코드리뷰 P1). 같은 초에
 * 발급되게 맞추고, 그렇지 못한 실행은 건너뜀으로 표시한다({@code assumeSameSecond}).
 *
 * <p>Redis는 로컬 Redis(localhost:6379)를 쓴다(다른 IT와 같은 알려진 격리 한계, CLAUDE.md 백로그). 다른
 * IT가 같은 userId로 남긴 {@code user:status}/{@code user:tokenEpoch}/{@code login:fail} 키가 결과를
 * 바꾸지 않도록 매 테스트 전에 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
class RefreshTokenReuseAccessCutoffMariaDbIT {

    private static final String EMAIL = "reuse-cutoff@test.com";
    private static final String PASSWORD = "Abcd1234!";

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
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;
    @Autowired
    private RefreshTokenHasher refreshTokenHasher;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;

    private WithdrawalTestSeed seed;
    private long userId;
    private String originalRefreshToken;

    @BeforeEach
    void setUp() {
        seed = new WithdrawalTestSeed(jdbc);
        seed.wipeAll();
        userId = seed.user(EMAIL, passwordEncoder.encode(PASSWORD), "ACTIVE", null);
        redis.delete(List.of("user:status:" + userId, "user:tokenEpochMs:" + userId, "login:fail:" + EMAIL));
        originalRefreshToken = jwtTokenProvider.createRefreshToken(userId);
        seed.refreshToken(userId, refreshTokenHasher.hash(originalRefreshToken), false);
    }

    @Test
    void 재발급_경로의_재사용_탐지_뒤_기존_Access_Token은_401이고_새로_로그인하면_정상이다() throws Exception {
        String victimAccessToken = jwtTokenProvider.createAccessToken(userId, "USER");
        expectMe(victimAccessToken, 200);

        waitForStartOfNextSecond();
        TokenResponse attacker = authService.refreshAccessToken(originalRefreshToken);
        Instant attackerIssuedAt = jwtTokenProvider.getIssuedAt(attacker.accessToken());
        expectMe(attacker.accessToken(), 200);
        assertThatThrownBy(() -> authService.refreshAccessToken(originalRefreshToken))
                .isInstanceOf(InvalidRefreshTokenException.class);
        Instant afterDetection = Instant.now();
        verify(auditLogger).logRefreshTokenReuseDetected(userId);

        // 탐지 직전에 회전으로 받은 공격자의 Access Token과 정상 사용자가 쓰던 토큰 모두 막힌다.
        expectMe(attacker.accessToken(), 401);
        expectMe(victimAccessToken, 401);
        // 공격자의 후속 Refresh Token도 폐기돼 다시 회전할 수 없다.
        assertThatThrownBy(() -> authService.refreshAccessToken(attacker.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        expectMe(login(), 200);
        assumeSameSecond(attackerIssuedAt, afterDetection);
    }

    @Test
    void 로그아웃_경로의_재사용_탐지_뒤에도_기존_Access_Token은_401이고_새로_로그인하면_정상이다() throws Exception {
        expectMe(jwtTokenProvider.createAccessToken(userId, "USER"), 200);

        waitForStartOfNextSecond();
        TokenResponse attacker = authService.refreshAccessToken(originalRefreshToken);
        Instant attackerIssuedAt = jwtTokenProvider.getIssuedAt(attacker.accessToken());
        expectMe(attacker.accessToken(), 200);
        // 정상 사용자가 (이미 rotation된) 원래 토큰으로 로그아웃한다 — 예외 없이 끝나야 한다.
        authService.logout(userId, originalRefreshToken);
        Instant afterDetection = Instant.now();
        verify(auditLogger).logRefreshTokenReuseDetected(userId);

        expectMe(attacker.accessToken(), 401);
        expectMe(login(), 200);
        assumeSameSecond(attackerIssuedAt, afterDetection);
    }

    /**
     * 다음 초가 시작될 때까지 기다린다 — 뒤이은 공격자 회전과 재사용 탐지(수십 밀리초)가 같은 초에 들어오게 해,
     * "같은 초에 발급된 공격자 토큰도 막힌다"를 우연이 아니라 매번 검증한다. 초 경계를 넘기려는 옛 sleep과
     * 반대 목적이다.
     */
    private static void waitForStartOfNextSecond() throws InterruptedException {
        Thread.sleep(1000 - Instant.now().toEpochMilli() % 1000 + 5);
    }

    /**
     * 공격자 토큰 발급과 탐지가 같은 초였는지 확인한다. 보안 단언(401)은 모두 끝난 뒤 마지막에 두어, 느린 환경에서
     * 초 경계를 넘긴 실행은 실패가 아니라 건너뜀으로 표시된다 — 그 실행은 "같은 초" 경합을 재현하지 못했을 뿐
     * 결함은 아니다. 건너뜀이 반복되면 이 테스트가 옛 결함(초 단위 비교)을 더는 잡지 못한다는 신호다.
     */
    private static void assumeSameSecond(Instant attackerIssuedAt, Instant afterDetection) {
        assumeTrue(attackerIssuedAt.getEpochSecond() == afterDetection.getEpochSecond(),
                "공격자 토큰 발급과 탐지가 초 경계를 넘겨 같은 초 경합을 재현하지 못했다");
    }

    private void expectMe(String accessToken, int expectedStatus) throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().is(expectedStatus));
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }
}
