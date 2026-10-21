package com.jiseong.homesense.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.jiseong.homesense.user.service.WithdrawalTestSeed;

/**
 * Redis 장애 시 강등을 실제 필터 체인(MockMvc)·MariaDB로 검증한다(COM-SEC-01·COM-CACHE-01·COM-EXC-01). Redis는 공용
 * 컨테이너(RedisContainerSupport)가 아니라 이 클래스 전용 컨테이너를 띄웠다가 중지한다 — 공용 컨테이너를 멈추면 같은 JVM의 다른
 * IT가 깨진다.
 *
 * <p>Redis가 살아 있을 때 유효한 Access Token으로 보호 API가 200임을 먼저 확인한 뒤 Redis를 멈추고 본다: (1) 같은 토큰을 붙인
 * 공개 조회(@Cacheable 단지 상세·인기 단지)는 200 — 필터는 비로그인으로 강등하고 캐시는 미스로 처리돼 DB에서 읽는다, (2) 같은
 * 토큰으로 인증 필요 API는 401이 아니라 503 AUTH_TEMPORARILY_UNAVAILABLE, (3) 로그인은 잠금 예약(Redis)을 할 수 없어 비밀번호를
 * 비교하지 않고 503 SERVICE_TEMPORARILY_UNAVAILABLE(막힌 쪽 실패).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
class RedisOutageDegradationMariaDbIT {

    private static final String EMAIL = "redis-outage-it@test.com";
    private static final String PASSWORD = "Abcd1234!";

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:10.11")
            .withDatabaseName("homesense_it")
            .withUsername("homesense")
            .withPassword("homesense");

    /** 이 클래스 전용. 테스트 중에 멈춘다(@Container로 두지 않는다 — 수명은 이 클래스가 직접 다룬다). */
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", MARIADB::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/withdrawn-user-purge-schema.sql");
        // 컨테이너를 멈춘 뒤에도 같은 호스트·포트를 계속 가리킨다(포트는 시작 때 한 번 정해진다).
        String host = REDIS.getHost();
        Integer port = REDIS.getMappedPort(6379);
        registry.add("spring.data.redis.host", () -> host);
        registry.add("spring.data.redis.port", () -> port);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void Redis가_멈추면_공개_조회는_200_인증_필요_API는_503_AUTH_로그인은_503이다() throws Exception {
        WithdrawalTestSeed seed = new WithdrawalTestSeed(jdbc);
        seed.wipeAll();
        long userId = seed.user(EMAIL, passwordEncoder.encode(PASSWORD), "ACTIVE", null);
        long complexId = seed.complex("REDIS-OUTAGE-1");
        String bearer = "Bearer " + jwtTokenProvider.createAccessToken(userId, "USER");

        // Redis가 살아 있으면 이 토큰은 유효하다.
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer))
                .andExpect(status().isOk());

        REDIS.stop();

        // (1) 토큰을 붙인 공개 조회 — 필터 강등 + 캐시 조회·저장 실패를 삼키고 DB에서 읽는다.
        mockMvc.perform(get("/api/complexes/{id}", complexId).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complexId").value(complexId));
        mockMvc.perform(get("/api/complexes/popular").header("Authorization", bearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/complexes/{id}", complexId))
                .andExpect(status().isOk());

        // (2) 같은 토큰으로 인증 필요 API — 토큰이 무효인 게 아니라 확인할 수 없는 것이라 401이 아니다.
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_TEMPORARILY_UNAVAILABLE"));

        // 토큰 없이 인증 필요 API는 Redis와 무관하게 기존대로 401이다.
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // (3) 로그인 — 잠금 예약을 건너뛰고 진행하지 않는다(막힌 쪽 실패).
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SERVICE_TEMPORARILY_UNAVAILABLE"));
    }
}
