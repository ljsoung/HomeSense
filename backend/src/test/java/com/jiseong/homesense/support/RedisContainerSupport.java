package com.jiseong.homesense.support;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트(@Tag("integration"))용 Redis — 개발용 로컬 Redis(localhost:6379) 대신 Testcontainers Redis에 붙인다.
 * 전에는 IT가 로컬 Redis에 잠금 카운터·캐시·prefix 맵 버전을 그대로 써 개발자의 로컬 상태를 바꿨고, 로컬 Redis가 없으면
 * IT가 실패했다(CLAUDE.md 백로그 "테스트 격리").
 *
 * <p>컨테이너는 JVM당 하나다(싱글턴 — Ryuk가 JVM 종료 시 정리한다). IT 클래스마다 MariaDB 컨테이너가 달라 Spring 컨텍스트도
 * 클래스마다 새로 뜨므로, 클래스 시작 전에 {@code FLUSHALL}로 비워 앞 클래스가 남긴 키(같은 userId의 잠금 카운트 등 — MariaDB
 * 컨테이너마다 userId가 1부터 시작한다)가 다음 클래스에 새지 않게 한다. 같은 클래스 안의 테스트끼리는 각 테스트가 정리한다.
 *
 * <p>사용법: {@code @SpringBootTest} IT가 이 클래스를 상속한다. 자기 {@code @DynamicPropertySource}(MariaDB)는 그대로 두면 된다 —
 * 상위 클래스의 것과 함께 적용된다.
 */
public abstract class RedisContainerSupport {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @BeforeAll
    static void flushRedis() throws IOException, InterruptedException {
        ExecResult result = REDIS.execInContainer("redis-cli", "FLUSHALL");
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Redis FLUSHALL 실패: " + result.getStderr());
        }
    }
}
