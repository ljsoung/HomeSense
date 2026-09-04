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
    void null_값은_캐시하지_않는다() {
        RedisCacheConfiguration configuration = cacheConfig.redisCacheConfiguration();

        assertThat(configuration.getAllowCacheNullValues()).isFalse();
    }
}
