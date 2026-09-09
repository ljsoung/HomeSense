package com.jiseong.homesense.region.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-FAV-01.addFavoriteRegion() — 존재하지 않는 legalDongCd로 등록을 시도한 경우. RGN 도메인의
 * 엔티티({@code LegalDistrictCode})가 존재하지 않는 상황이라 그 엔티티를 소유한 region 패키지에
 * 둔다(ComplexNotFoundException이 원래 complex.exception 소속이었던 것과 같은 위치 기준, CLAUDE.md
 * "SVC-CPX-01/SVC-FAV-01가 공유" 승격 이전 상태 참고) — 지금은 FAV만 던지지만 다른 도메인이 같은
 * 검사를 필요로 하게 되면 그때 common.exception으로 승격한다.
 */
public class RegionNotFoundException extends BusinessException {

    public RegionNotFoundException() {
        super("REGION_NOT_FOUND", "존재하지 않는 지역입니다", HttpStatus.NOT_FOUND);
    }
}
