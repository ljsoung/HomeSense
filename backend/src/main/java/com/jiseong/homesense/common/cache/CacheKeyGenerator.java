package com.jiseong.homesense.common.cache;

import java.lang.reflect.Method;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

/**
 * COM-CACHE-01. 파라미터가 하나뿐인 조회(getDetail(complexId), autocomplete(query) 등)는
 * {@code @Cacheable(key = "#complexId")}처럼 SpEL로 직접 지정하면 되지만, 파라미터가 여러 개인
 * 조회(예: 향후 추가될 페이징·필터 검색)에서 기본 SimpleKeyGenerator를 쓰면 SimpleKey#toString()이
 * "SimpleKey [a,b,c]" 형태로 나와 Redis에서 사람이 읽기 어렵다. 이 KeyGenerator는 그 대안이다.
 *
 * <p>파라미터가 1개면 그 값을 그대로 키로 쓴다 — 다른 값과 섞일 여지가 없어 사람이 읽기 좋은
 * "domain::param" 형태(RedisCache가 cacheName 뒤에 "::"를 붙임)가 그대로 성립한다.
 *
 * <p>파라미터가 2개 이상이면 각 값을 "길이:값" 형태로 길이 접두(length-prefix)해 이어붙인다(코드리뷰
 * PR #14에서 지적됨). 단순히 ":"로만 이어붙이면 값 자체에 ":"가 들어있는 경우 서로 다른 파라미터
 * 조합이 같은 키로 충돌할 수 있다 — 예를 들어 ("a:b", "c")와 ("a", "b:c")가 똑같이 "a:b:c"가 되어
 * 요청 A가 요청 B의 캐시를 그대로 받아가는 캐시 오염이 생긴다. 길이 접두는 각 세그먼트를 몇 글자
 * 읽어야 하는지 값의 내용과 무관하게 고정하므로 이 충돌이 원천적으로 불가능하다. null은 실제
 * 문자열 "null"과 구분하기 위해 별도로 "-1:" 마커를 쓴다(String.valueOf(null)이 우연히 리터럴
 * "null"과 같아지는 충돌도 같은 이유로 막아야 한다).
 */
@Component("cacheKeyGenerator")
public class CacheKeyGenerator implements KeyGenerator {

    private static final String SEPARATOR = ":";
    private static final String NULL_PREFIX = "-1:";

    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (params.length == 0) {
            return method.getName();
        }
        if (params.length == 1) {
            return String.valueOf(params[0]);
        }

        StringBuilder key = new StringBuilder();
        for (Object param : params) {
            appendLengthPrefixed(key, param);
        }
        return key.toString();
    }

    private void appendLengthPrefixed(StringBuilder key, Object param) {
        if (param == null) {
            key.append(NULL_PREFIX);
            return;
        }
        String value = String.valueOf(param);
        key.append(value.length()).append(SEPARATOR).append(value);
    }
}
