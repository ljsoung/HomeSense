package com.jiseong.homesense.batch.geocoding;

/** BAT-GEO-01. 5xx·네트워크 오류 — 다시 보내면 성공할 수 있는 실패. {@link #getMessage()}가 로그용 사유다. */
public class GeocodingTransientException extends RuntimeException {

    public GeocodingTransientException(String reason) {
        super(reason);
    }

    public GeocodingTransientException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
