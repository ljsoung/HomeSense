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
 * <p>null 캐싱은 여기서 막지 않는다({@code disableCachingNullValues()}를 의도적으로 호출하지 않음) —
 * 코드리뷰(PR #14)에서 지적된 대로, 그 메서드는 "null 결과는 캐시에 저장하지 않고 조용히 건너뛴다"가
 * 아니라 "null을 캐시에 저장하려는 시도 자체를 IllegalArgumentException으로 거부한다"로 동작한다
 * (AbstractValueAdaptingCache.toStoreValue()). 즉 캐시 설정에서 막아버리면, {@code unless} 조건 없이
 * null을 정상 반환하는 {@code @Cacheable} 메서드가 생기는 순간 그 호출이 예외로 깨진다 — "없음"을
 * TTL 동안 캐싱하지 않으려는 의도보다 훨씬 위험한 부작용이다. 이 세 캐시(complexDetail/
 * popularComplexes/regionAutocomplete)의 실제 조회 서비스는 "없음"을 null이 아니라 예외(404)나 빈
 * 컬렉션으로 표현할 가능성이 높아 null 캐싱 자체가 사실상 일어나지 않을 것으로 보이지만, 혹시라도
 * null을 정말 반환해야 하는 캐시 메서드가 생기면 이 설정을 건드리지 말고 그 {@code @Cacheable}
 * 애노테이션에 {@code unless = "#result == null"}을 붙여 해당 호출 지점에서만 캐싱을 건너뛰게 하라.
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
