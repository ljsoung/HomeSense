package com.jiseong.homesense.complex.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.jiseong.homesense.complex.dto.BoundsCondition;
import com.jiseong.homesense.complex.dto.ComplexSearchCondition;
import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;
import com.jiseong.homesense.complex.dto.MapFilterCondition;
import com.jiseong.homesense.complex.entity.Complex;

interface ComplexRepositoryCustom {

    Page<ComplexSummaryResponse> search(ComplexSearchCondition condition, Pageable pageable);

    /** limitPlusOne건까지 조회해 상한 초과 여부(truncated)를 호출부가 판단할 수 있게 한다. */
    List<Complex> searchInBounds(BoundsCondition bounds, MapFilterCondition filter, int limitPlusOne);
}
