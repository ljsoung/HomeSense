package com.jiseong.homesense.region.dto;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jiseong.homesense.region.entity.LegalDistrictCode;

/**
 * UIC-03 자동완성 결과 한 건. legalDongCd는 SVC-TRD-01의 TradeSearchCondition.legalDongCd 필터가
 * 프론트에서 그대로 전달할 수 있도록 반드시 포함한다(CLAUDE.md SVC-TRD-01 절 — RGN-01 착수 시
 * 확정해야 할 전제로 명시돼 있던 부분).
 */
public record RegionAutocompleteResponse(String legalDongCd, String fullPath) {

    public static RegionAutocompleteResponse from(LegalDistrictCode code) {
        String fullPath = Stream.of(code.getSidoName(), code.getSigunguName(), code.getEupmyeondongName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
        return new RegionAutocompleteResponse(code.getLegalDongCd(), fullPath);
    }
}
