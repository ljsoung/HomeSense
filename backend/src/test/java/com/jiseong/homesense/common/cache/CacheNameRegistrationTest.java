package com.jiseong.homesense.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.interceptor.CacheOperation;
import org.springframework.cache.interceptor.CacheOperationSource;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * 애플리케이션 빈에 선언된 모든 캐시 이름이 {@link CacheConfig#cacheConfigurations()}에 등록됐는지 검증한다.
 *
 * <p>미등록 캐시는 자동 생성되지 않으므로({@code disableCreateOnMissingCache}) 등록 누락은 그 캐시의 첫 호출에서
 * "Cannot find cache"로 실패한다. {@code CacheConfigTest}는 {@link CacheNames} 상수만 검사하므로,
 * {@code @Cacheable("literal")}처럼 상수를 거치지 않은 이름은 여기서 잡는다.
 *
 * <p>캐시 이름은 Spring이 실제 캐시 인터셉터에 쓰는 {@link CacheOperationSource}에서 읽는다 — {@code @Cacheable}·
 * {@code @CachePut}·{@code @CacheEvict}·{@code @Caching}과 클래스 레벨 {@code @CacheConfig} 기본값이 이미 합쳐진
 * 결과라, 애노테이션을 직접 파싱할 때 생길 수 있는 누락이 없다.
 */
@SpringBootTest
class CacheNameRegistrationTest {

    private static final String APP_PACKAGE = "com.jiseong.homesense.";

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private CacheOperationSource cacheOperationSource;
    @Autowired
    private RedisCacheManager cacheManager;

    @Test
    void 빈에_선언된_모든_캐시_이름이_값_타입과_함께_등록돼_있다() {
        Map<String, Set<String>> declared = declaredCacheNames();
        Set<String> registered = cacheManager.getCacheConfigurations().keySet();

        // 스캔 자체가 동작하는지 — 알려진 캐시를 전부 찾아야 한다.
        assertThat(declared.keySet()).contains(CacheNames.COMPLEX_DETAIL, CacheNames.POPULAR_COMPLEXES,
                CacheNames.REGION_AUTOCOMPLETE, CacheNames.POPULAR_KEYWORDS);

        Map<String, Set<String>> unregistered = new TreeMap<>(declared);
        unregistered.keySet().removeAll(registered);
        assertThat(unregistered)
                .as("CacheConfig.cacheConfigurations()에 등록되지 않은 캐시 이름(이름 → 선언 위치). "
                        + "CacheNames 상수로 선언하고 값 타입과 함께 등록하라")
                .isEmpty();
    }

    /** 캐시 이름 → 그 이름을 선언한 메서드들. */
    private Map<String, Set<String>> declaredCacheNames() {
        Map<String, Set<String>> result = new TreeMap<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = applicationContext.getType(beanName);
            if (type == null) {
                continue;
            }
            Class<?> targetClass = ClassUtils.getUserClass(type);
            if (!targetClass.getName().startsWith(APP_PACKAGE)) {
                continue;
            }
            for (Method method : ReflectionUtils.getUniqueDeclaredMethods(targetClass)) {
                Collection<CacheOperation> operations = cacheOperationSource.getCacheOperations(method, targetClass);
                if (operations == null) {
                    continue;
                }
                for (CacheOperation operation : operations) {
                    for (String cacheName : operation.getCacheNames()) {
                        result.computeIfAbsent(cacheName, k -> new TreeSet<>())
                                .add(targetClass.getSimpleName() + "." + method.getName());
                    }
                }
            }
        }
        return result;
    }
}
