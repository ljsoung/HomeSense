package com.jiseong.homesense.common.cache;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.LogFactory;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.jiseong.homesense.complex.dto.ComplexDetailResponse;
import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;
import com.jiseong.homesense.region.dto.RegionAutocompleteResponse;
import com.jiseong.homesense.search.dto.PopularKeywordResponse;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;

/**
 * COM-CACHE-01. {@code @Cacheable}/{@code @CacheEvict}만으로 서비스 계층이 캐시를 적용할 수 있도록
 * RedisCacheManager를 구성한다(FR-7.3). 캐시 키는 RedisCache의 기본 규칙(cacheName + "::" + 생성된 키)을
 * 그대로 따르므로 CLAUDE.md가 정의한 "도메인::파라미터" 형식(complexDetailV3::{complexId} 등)이 별도
 * 커스터마이징 없이 성립한다.
 *
 * <p>값 직렬화는 캐시마다 값 타입을 명시한 Jackson 3 {@link JacksonJsonRedisSerializer}를 쓴다(2026-09-27
 * hotfix). 이전에는 모든 캐시가 {@code GenericJacksonJsonRedisSerializer.enableUnsafeDefaultTyping()} 하나를
 * 공유했는데, 이 타이핑은 final 타입에 타입 정보를 붙이지 않는다 — {@code Stream.toList()}가 돌려주는
 * {@code ImmutableCollections.ListN}(final)은 최상위 배열이 타입 정보 없이 저장되고, 읽을 때는 타입 정보를
 * 기대해 {@code SerializationException}이 났다. 리스트를 캐싱하는 세 캐시(popularComplexesV3/
 * regionAutocomplete/popularKeywords)가 캐시 히트마다 500이었다. 반환값을 {@code ArrayList}로 감싸도 되지만
 * 리스트 캐시가 생길 때마다 그 규칙을 기억해야 하므로, 타입을 설정에서 명시하는 쪽을 택했다. 역직렬화가
 * 저장된 {@code @class}가 아니라 설정의 타입을 따르므로 임의 타입 역직렬화 경로(unsafe default typing)도
 * 함께 없어진다.
 *
 * <p>대신 <b>새 캐시는 반드시 {@link #cacheConfigurations}에 값 타입과 함께 등록해야 한다</b> — 미등록
 * 이름을 자동 생성하지 않도록({@code disableCreateOnMissingCache}) 해 두어, 등록을 잊으면 그 캐시를 처음
 * 쓰는 순간 {@code IllegalArgumentException}("Cannot find cache")으로 드러난다.
 *
 * <p>캐시 조회·저장·삭제 실패(Redis 장애·타임아웃, 옛 형식 엔트리 역직렬화 실패)는 {@link #errorHandler()}가
 * 로그만 남기고 삼킨다 — 조회 실패는 캐시 미스로 처리돼 DB에서 다시 읽고, 요청은 500이 되지 않는다. 배포 전에
 * 옛 직렬화기로 저장된 엔트리도 이 경로로 미스 처리된 뒤 새 형식으로 덮어써진다.
 *
 * <p>null 캐싱은 여기서 막지 않는다({@code disableCachingNullValues()}를 의도적으로 호출하지 않음) —
 * 코드리뷰(PR #14)에서 지적된 대로, 그 메서드는 "null 결과는 캐시에 저장하지 않고 조용히 건너뛴다"가
 * 아니라 "null을 캐시에 저장하려는 시도 자체를 IllegalArgumentException으로 거부한다"로 동작한다
 * (AbstractValueAdaptingCache.toStoreValue()). 즉 캐시 설정에서 막아버리면, {@code unless} 조건 없이
 * null을 정상 반환하는 {@code @Cacheable} 메서드가 생기는 순간 그 호출이 예외로 깨진다 — "없음"을
 * TTL 동안 캐싱하지 않으려는 의도보다 훨씬 위험한 부작용이다. 이 세 캐시(complexDetailV3/
 * popularComplexesV3/regionAutocomplete)의 실제 조회 서비스는 "없음"을 null이 아니라 예외(404)나 빈
 * 컬렉션으로 표현할 가능성이 높아 null 캐싱 자체가 사실상 일어나지 않을 것으로 보이지만, 혹시라도
 * null을 정말 반환해야 하는 캐시 메서드가 생기면 이 설정을 건드리지 말고 그 {@code @Cacheable}
 * 애노테이션에 {@code unless = "#result == null"}을 붙여 해당 호출 지점에서만 캐싱을 건너뛰게 하라.
 *
 * <p>캐시에 담기는 DTO에 새 필드를
 * 추가할 때는 옛 캐시 이름을 그대로 두지 마라 — Redis가 배포 사이에도 살아남으면 옛 필드 구성으로
 * 직렬화된 엔트리가 새 필드를 null로 채운 채 역직렬화되고, 그 null이 소비 로직에서 조용히 다른
 * 분기(예: RecentViewService.record()의 스킵 처리)를 타 버릴 수 있다(Codex 코드리뷰 지적,
 * {@link com.jiseong.homesense.complex.service.ComplexDetailCache} 참고). 캐시 이름을 올려 옛
 * 엔트리를 아예 다시 읽지 않게 하고, 옛 엔트리는 각자의 TTL로 자연 만료되게 두면 된다 —
 * {@code popularComplexes} → {@code popularComplexesV2}도 같은 이유로 버전업했다(matchMethod/floor
 * 필드 추가, CPX-RCV-RGN 카드 표시 필드 보강 작업). 이후 rentType/monthlyRentAmount 추가로 V3가 됐다.
 *
 * <p>{@code popularKeywords}(SVC-SEARCH-01, 신규 제안 — CLAUDE.md API-SEARCH-01 절 참고)는 이 세 캐시와
 * 무효화 트리거 자체가 다르다 — 검색 실행마다 evict하면 쓰기가 빈번해 캐시 이득이 없으므로 evict
 * 트리거를 두지 않고 짧은 TTL(1시간) 만료로만 자연 갱신되게 한다. 기본 TTL(24h)을 그대로 쓰면 신규
 * 급상승 키워드가 하루 종일 반영되지 않아 "인기 검색어" 취지에 맞지 않아 별도 TTL을 건다.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final Duration POPULAR_KEYWORDS_TTL = Duration.ofHours(1);

    private final JsonMapper cacheMapper = JsonMapper.builder().build();

    /** 공통 설정(TTL·키 직렬화·null 허용). 값 직렬화는 {@link #cacheConfigurations}가 캐시마다 붙인다. */
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()));
    }

    /** 캐시 이름 → 값 타입이 명시된 설정. 새 캐시는 여기에 등록한다. */
    Map<String, RedisCacheConfiguration> cacheConfigurations() {
        TypeFactory types = cacheMapper.getTypeFactory();
        RedisCacheConfiguration base = redisCacheConfiguration();
        return Map.of(
                CacheNames.COMPLEX_DETAIL,
                withValueType(base, types.constructType(ComplexDetailResponse.class)),
                CacheNames.POPULAR_COMPLEXES,
                withValueType(base, types.constructCollectionType(List.class, ComplexSummaryResponse.class)),
                CacheNames.REGION_AUTOCOMPLETE,
                withValueType(base, types.constructCollectionType(List.class, RegionAutocompleteResponse.class)),
                CacheNames.POPULAR_KEYWORDS,
                withValueType(base, types.constructCollectionType(List.class, PopularKeywordResponse.class))
                        .entryTtl(POPULAR_KEYWORDS_TTL));
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(redisCacheConfiguration())
                .withInitialCacheConfigurations(cacheConfigurations())
                .disableCreateOnMissingCache()
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(LogFactory.getLog(CacheConfig.class), false);
    }

    private RedisCacheConfiguration withValueType(RedisCacheConfiguration base, JavaType valueType) {
        return base.serializeValuesWith(
                SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(cacheMapper, valueType)));
    }
}
