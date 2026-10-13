package com.jiseong.homesense.complex.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.jiseong.homesense.complex.dto.BoundsCondition;
import com.jiseong.homesense.complex.dto.ComplexMapPointResponse;
import com.jiseong.homesense.complex.dto.ComplexSearchCondition;
import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;

interface ComplexRepositoryCustom {

    /**
     * @param regionPrefix condition.regionCode()를 {@code RegionCodePrefixResolver}로 바꾼 법정동코드 prefix.
     *                     null이면 지역 조건 없음.
     */
    Page<ComplexSummaryResponse> search(ComplexSearchCondition condition, String regionPrefix, Pageable pageable);

    /**
     * 좌표 범위 안에서 condition을 만족하는 거래가 있는 단지를 complex_id 순으로 limitPlusOne건까지 돌려준다 — 한 건 더
     * 받아 호출부가 상한 초과 여부(truncated)를 판단한다. condition의 regionCode·keyword·sort는 쓰지 않는다.
     */
    List<ComplexMapPointResponse> searchInBounds(BoundsCondition bounds, ComplexSearchCondition condition,
            int limitPlusOne);
}
