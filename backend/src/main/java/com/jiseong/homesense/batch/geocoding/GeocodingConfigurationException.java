package com.jiseong.homesense.batch.geocoding;

/** BAT-GEO-01. 401·403 — 키·앱 설정 문제라 남은 단지도 전부 같은 결과다. 그 실행을 멈춘다. */
public class GeocodingConfigurationException extends RuntimeException {

    public GeocodingConfigurationException(String message) {
        super(message);
    }
}
