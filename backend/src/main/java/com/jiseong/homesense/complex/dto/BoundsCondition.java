package com.jiseong.homesense.complex.dto;

import java.math.BigDecimal;
import java.util.Locale;

import com.jiseong.homesense.complex.exception.InvalidBoundsException;

/**
 * MAP-01 bounds 파라미터(남서/북동 좌표) 파싱 결과. 카카오맵 SDK가 넘기는 형식을 그대로 따라
 * "swLat,swLng,neLat,neLng" 콤마 구분 4개 숫자 문자열을 기대한다.
 */
public record BoundsCondition(BigDecimal swLat, BigDecimal swLng, BigDecimal neLat, BigDecimal neLng) {

    public static BoundsCondition parse(String bounds) {
        if (bounds == null || bounds.isBlank()) {
            throw new InvalidBoundsException();
        }

        String[] parts = bounds.split(",");
        if (parts.length != 4) {
            throw new InvalidBoundsException();
        }

        try {
            BigDecimal swLat = new BigDecimal(parts[0].trim());
            BigDecimal swLng = new BigDecimal(parts[1].trim());
            BigDecimal neLat = new BigDecimal(parts[2].trim());
            BigDecimal neLng = new BigDecimal(parts[3].trim());

            if (swLat.compareTo(neLat) > 0 || swLng.compareTo(neLng) > 0) {
                throw new InvalidBoundsException();
            }

            return new BoundsCondition(swLat, swLng, neLat, neLng);
        } catch (NumberFormatException e) {
            throw new InvalidBoundsException();
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s,%s,%s,%s", swLat, swLng, neLat, neLng);
    }
}
