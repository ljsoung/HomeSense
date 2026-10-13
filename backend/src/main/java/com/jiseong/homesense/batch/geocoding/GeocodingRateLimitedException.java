package com.jiseong.homesense.batch.geocoding;

/**
 * BAT-GEO-01. 429 — 카카오가 요청 한도(초당 또는 일일 쿼터)를 넘었다고 답했다. 그 실행 전체를 즉시 멈춘다.
 * 재시도하면 같은 한도에 계속 걸려 호출 수만 늘고, 일일 쿼터 초과라면 그날은 다시 보내도 소용없다.
 */
public class GeocodingRateLimitedException extends RuntimeException {

    public GeocodingRateLimitedException(String message) {
        super(message);
    }
}
