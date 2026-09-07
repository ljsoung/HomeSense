package com.jiseong.homesense.complex.dto;

import java.math.BigDecimal;

import com.jiseong.homesense.complex.entity.Complex;

/** MAP-01 마커 하나. 클러스터링은 카카오맵 SDK가 프론트에서 수행하므로 원시 좌표만 제공한다. */
public record ComplexMapPointResponse(
        Long complexId, String complexName, BigDecimal latitude, BigDecimal longitude, String locationPrecision) {

    public static ComplexMapPointResponse from(Complex complex) {
        return new ComplexMapPointResponse(complex.getComplexId(), complex.getComplexName(),
                complex.getLatitude(), complex.getLongitude(), complex.getLocationPrecision());
    }
}
