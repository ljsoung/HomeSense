package com.jiseong.homesense.common.cache;

import java.time.Duration;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * COM-CACHE-01. {@code @Cacheable}/{@code @CacheEvict}만으로 서비스 계층이 캐시를 적용할 수 있도록
 * RedisCacheManager를 구성한다(FR-7.3). 캐시 키는 RedisCache의 기본 규칙(cacheName + "::" + 생성된 키)을
 * 그대로 따르므로 CLAUDE.md가 정의한 "도메인::파라미터" 형식(complexDetail::{complexId} 등)이 별도
 * 커스터마이징 없이 성립한다.
 *
 * <p>값 직렬화는 Jackson 3 기반 {@link GenericJacksonJsonRedisSerializer}를 쓴다 — 이 프로젝트가
 * Spring Boot 4/Spring Framework 7 위에서 Jackson 3(tools.jackson)을 표준으로 쓰기 때문에, 구버전
 * Jackson 2 계열인 GenericJackson2JsonRedisSerializer는 의도적으로 쓰지 않는다. 캐시 값은 이 서비스가
 * 직접 만든 DTO/엔티티뿐이라 역직렬화 시 신뢰할 수 없는 타입이 들어올 위험이 없으므로
 * enableUnsafeDefaultTyping()으로 다형적 타입 정보를 저장한다.
 *
 * <p>null 값은 캐시하지 않는다(disableCachingNullValues) — 조회 시점에 아직 없던 데이터가 나중에
 * 생성돼도 TTL(24시간) 동안 "없음" 응답이 그대로 굳어버리는 걸 막기 위함이다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer));
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory,
                                           RedisCacheConfiguration redisCacheConfiguration) {
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .build();
    }
}
