package com.jiseong.homesense.common.cache;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

/**
 * COM-CACHE-01. 파라미터가 하나뿐인 조회(getDetail(complexId), autocomplete(query) 등)는
 * {@code @Cacheable(key = "#complexId")}처럼 SpEL로 직접 지정하면 되지만, 파라미터가 여러 개인
 * 조회(예: 향후 추가될 페이징·필터 검색)에서 기본 SimpleKeyGenerator를 쓰면 SimpleKey#toString()이
 * "SimpleKey [a,b,c]" 형태로 나와 Redis에서 사람이 읽기 어렵다. 이 KeyGenerator는 파라미터를
 * ":"로 이어붙여 사람이 읽을 수 있는 키를 만든다 — RedisCache가 cacheName 뒤에 "::"를 붙이므로
 * 최종 키는 "domain::param1:param2" 형태가 된다.
 *
 * <p>기본 KeyGenerator로 전역 등록하지 않고, 필요한 {@code @Cacheable}에
 * {@code keyGenerator = "cacheKeyGenerator"}로 명시적으로 지정해 쓴다.
 */
@Component("cacheKeyGenerator")
public class CacheKeyGenerator implements KeyGenerator {

    private static final String SEPARATOR = ":";

    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (params.length == 0) {
            return method.getName();
        }
        return Arrays.stream(params)
                .map(String::valueOf)
                .collect(Collectors.joining(SEPARATOR));
    }
}
