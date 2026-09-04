package com.jiseong.homesense.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();

    @Test
    void 기본_TTL은_24시간이다() {
        RedisCacheConfiguration configuration = cacheConfig.redisCacheConfiguration();

        Duration ttl = configuration.getTtlFunction().getTimeToLive("any-key", "any-value");

        assertThat(ttl).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void null_캐싱을_막지_않는다_disableCachingNullValues는_저장_시도_자체를_예외로_거부하기_때문이다() {
        RedisCacheConfiguration configuration = cacheConfig.redisCacheConfiguration();

        // 코드리뷰(PR #14)에서 지적된 대로 disableCachingNullValues()는 "null은 조용히 캐싱을
        // 건너뛴다"가 아니라 "null을 캐시에 넣으려는 시도를 IllegalArgumentException으로 거부한다"라
        // unless 조건 없는 @Cacheable(null 반환 가능)이 깨질 수 있어 의도적으로 켜지 않는다.
        assertThat(configuration.getAllowCacheNullValues()).isTrue();
    }
}
