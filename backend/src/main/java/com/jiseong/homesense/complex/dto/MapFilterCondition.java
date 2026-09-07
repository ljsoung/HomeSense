package com.jiseong.homesense.complex.dto;

import java.util.List;

import com.jiseong.homesense.trade.entity.HousingType;

/** SVC-CPX-01.searchInBounds() 필터 입력. */
public record MapFilterCondition(List<HousingType> housingTypes) {
}
