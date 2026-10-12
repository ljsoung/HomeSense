package com.jiseong.homesense.batch.geocoding;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.common.config.GeocodingProperties;

/**
 * BAT-GEO-01이 Redis에 두는 두 가지 상태.
 * <ul>
 *   <li>{@code geo:fail:{complexId}} — 카카오가 "주소를 못 찾음"으로 답한 단지. TTL({@code notFoundRetryDays}) 동안 정기
 *       실행이 같은 단지를 매일 다시 호출하지 않게 한다. complex 테이블에 컬럼을 추가하지 않으려고 Redis에 둔다
 *       (미사용 컬럼 추가 금지 원칙 — 이 값은 지오코딩 재시도 억제에만 쓰인다). Redis가 비면(재시작·flush) 다음 실행이
 *       한 번 더 호출할 뿐이라 유실돼도 정확성 문제는 없다.</li>
 *   <li>{@code geo:calls:{yyyyMMdd}} — 그날(KST) 보낸 카카오 호출 수. 무료 쿼터는 앱 단위라(NFR-11) 일회성 러너와
 *       정기 실행이 같은 카운터를 센다. 이틀 뒤 만료된다.</li>
 * </ul>
 * Redis를 읽거나 쓰지 못하면 예외를 그대로 던진다 — 호출 수를 셀 수 없는 상태로 쿼터를 쓰지 않게 그 실행을 멈춘다.
 */
@Component
public class GeocodingRedisState {

    static final String FAIL_KEY_PREFIX = "geo:fail:";
    static final String CALLS_KEY_PREFIX = "geo:calls:";
    private static final Duration CALL_COUNTER_TTL = Duration.ofDays(2);
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final Duration notFoundTtl;

    public GeocodingRedisState(StringRedisTemplate redisTemplate, Clock clock, GeocodingProperties properties) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.notFoundTtl = Duration.ofDays(properties.notFoundRetryDays());
    }

    public boolean isMarkedNotFound(Long complexId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(FAIL_KEY_PREFIX + complexId));
    }

    public void markNotFound(Long complexId, String reason) {
        redisTemplate.opsForValue().set(FAIL_KEY_PREFIX + complexId, reason, notFoundTtl);
    }

    /** 호출 한 번을 센 뒤 오늘 누적 호출 수를 돌려준다. */
    public long recordCall() {
        String key = callsKey();
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, CALL_COUNTER_TTL);
        }
        return count == null ? 0L : count;
    }

    /** {@link #recordCall()}로 센 호출을 보내지 않았을 때(상한 초과) 되돌린다. */
    public void releaseCall() {
        redisTemplate.opsForValue().decrement(callsKey());
    }

    public long todayCalls() {
        String value = redisTemplate.opsForValue().get(callsKey());
        return value == null ? 0L : Long.parseLong(value);
    }

    private String callsKey() {
        return CALLS_KEY_PREFIX + LocalDate.now(clock).format(DAY);
    }
}
