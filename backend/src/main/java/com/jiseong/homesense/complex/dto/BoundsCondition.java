package com.jiseong.homesense.complex.dto;

import java.math.BigDecimal;
import java.util.Locale;

import com.jiseong.homesense.complex.exception.InvalidBoundsException;

/**
 * MAP-01 bounds 파라미터(남서/북동 좌표) 파싱 결과. 카카오맵 SDK가 넘기는 형식을 그대로 따라
 * "swLat,swLng,neLat,neLng" 콤마 구분 4개 숫자 문자열을 기대한다.
 *
 * <p>숫자가 아니거나 4개가 아니면, 위도가 -90~90·경도가 -180~180 밖이면, 남서가 북동보다 크면
 * {@link InvalidBoundsException}(400)이다. 남서와 북동이 같은 점(넓이 0)은 허용한다. 날짜변경선을 넘는 범위는
 * 국내 서비스라 지원하지 않는다(sw 경도가 ne 경도보다 크면 오류).
 */
public record BoundsCondition(BigDecimal swLat, BigDecimal swLng, BigDecimal neLat, BigDecimal neLng) {

    private static final BigDecimal MAX_LAT = BigDecimal.valueOf(90);
    private static final BigDecimal MAX_LNG = BigDecimal.valueOf(180);

    public static BoundsCondition parse(String bounds) {
        if (bounds == null || bounds.isBlank()) {
            throw new InvalidBoundsException();
        }

        // limit -1: "1,2,3,4,"처럼 끝에 빈 조각이 있어도 버리지 않고 5개로 세어 거절한다.
        String[] parts = bounds.split(",", -1);
        if (parts.length != 4) {
            throw new InvalidBoundsException();
        }

        BigDecimal swLat;
        BigDecimal swLng;
        BigDecimal neLat;
        BigDecimal neLng;
        try {
            swLat = new BigDecimal(parts[0].trim());
            swLng = new BigDecimal(parts[1].trim());
            neLat = new BigDecimal(parts[2].trim());
            neLng = new BigDecimal(parts[3].trim());
        } catch (NumberFormatException e) {
            throw new InvalidBoundsException();
        }

        if (!within(swLat, MAX_LAT) || !within(neLat, MAX_LAT) || !within(swLng, MAX_LNG) || !within(neLng, MAX_LNG)) {
            throw new InvalidBoundsException();
        }
        if (swLat.compareTo(neLat) > 0 || swLng.compareTo(neLng) > 0) {
            throw new InvalidBoundsException();
        }
        return new BoundsCondition(swLat, swLng, neLat, neLng);
    }

    private static boolean within(BigDecimal value, BigDecimal max) {
        return value.compareTo(max.negate()) >= 0 && value.compareTo(max) <= 0;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s,%s,%s,%s", swLat, swLng, neLat, neLng);
    }
}
