package com.jiseong.homesense.complex.dto;

import com.jiseong.homesense.trade.entity.MatchMethod;

/**
 * UIC-09 정밀/근사 배지(UI정의서 4.9절). 서버가 한 번 계산해 /search·/popular·/map·/complexes/{id} 응답에 같은 값을
 * 내린다 — 화면마다 matchMethod와 locationPrecision을 따로 조합하면 같은 단지의 배지가 화면마다 달라질 수 있다.
 *
 * <p>근사(APPROXIMATE)는 둘 중 하나라도 근사일 때다: 대표 거래가 유사 매칭(matchMethod = SIMILAR)이거나, 좌표가 키워드
 * 장소 검색으로 찾은 근사 위치(locationPrecision = APPROXIMATE, BAT-GEO-01)다. 정밀(PRECISE)은 대표 거래가 정확 매칭
 * (EXACT)이고 좌표가 근사가 아닐 때다 — 좌표가 아직 없어도(지오코딩 전·못 찾음) 정밀이다(지도에 핀이 없을 뿐 위치를
 * 잘못 보여 주지 않는다). 대표 거래가 없고 좌표도 근사가 아니면 판단할 근거가 없어 null이다(배지 없음 — DTL-01에서 거래가
 * 없는 단지의 기존 동작).
 */
public enum PrecisionBadge {
    PRECISE,
    APPROXIMATE;

    /** complex.location_precision의 근사 값. BAT-GEO-01이 쓰는 문자열과 같다. */
    static final String APPROXIMATE_LOCATION = "APPROXIMATE";

    public static PrecisionBadge of(MatchMethod matchMethod, String locationPrecision) {
        if (matchMethod == MatchMethod.SIMILAR || APPROXIMATE_LOCATION.equals(locationPrecision)) {
            return APPROXIMATE;
        }
        return matchMethod == MatchMethod.EXACT ? PRECISE : null;
    }
}
