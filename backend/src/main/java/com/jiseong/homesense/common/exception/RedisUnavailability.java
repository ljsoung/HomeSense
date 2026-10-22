package com.jiseong.homesense.common.exception;

import org.springframework.dao.DataAccessException;

/**
 * Redis 인프라 장애(연결 실패·명령 타임아웃·Lettuce 오류) 판별. JWT 필터의 강등(COM-SEC-01)과 전역 예외 처리기의 503 매핑
 * (COM-EXC-01)이 같은 기준을 쓴다.
 *
 * <p>Spring Data Redis는 Lettuce 예외를 {@code org.springframework.data.redis} 패키지의 {@link DataAccessException}
 * ({@code RedisConnectionFailureException}, {@code RedisSystemException} 등)으로 바꾸고, 명령 타임아웃은 공용
 * {@code org.springframework.dao.QueryTimeoutException}(원인 {@code io.lettuce.core.RedisCommandTimeoutException})으로 바꾼다.
 * 그래서 (1) Redis 패키지의 DataAccessException이거나 (2) 원인 사슬에 Lettuce 예외가 있는 DataAccessException만 Redis 장애로
 * 본다. JDBC의 QueryTimeoutException(MariaDB 타임아웃)은 원인이 Lettuce가 아니라 여기 해당하지 않는다. 직렬화 오류
 * ({@code SerializationException})는 DataAccessException이 아니라 해당하지 않는다(장애가 아니라 코드 문제다).
 */
public final class RedisUnavailability {

    private static final String SPRING_DATA_REDIS_PACKAGE = "org.springframework.data.redis";
    private static final String LETTUCE_PACKAGE = "io.lettuce.core";

    private RedisUnavailability() {
    }

    public static boolean isRedisUnavailable(Throwable e) {
        if (!(e instanceof DataAccessException)) {
            return false;
        }
        if (e.getClass().getPackageName().startsWith(SPRING_DATA_REDIS_PACKAGE)) {
            return true;
        }
        for (Throwable cause = e.getCause(); cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause.getClass().getPackageName().startsWith(LETTUCE_PACKAGE)) {
                return true;
            }
        }
        return false;
    }
}
