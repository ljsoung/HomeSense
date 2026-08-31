package com.jiseong.homesense.batch.matcher;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;

import lombok.RequiredArgsConstructor;

/**
 * BAT-MAT-01. trade 적재 시 국토부 API의 sgg_cd(5자리)를 legal_district_code의 legal_dong_cd(10자리)로 매핑한다.
 */
@Component
@RequiredArgsConstructor
public class LegalDistrictMatcher {

    private final LegalDistrictCodeRepository legalDistrictCodeRepository;

    /**
     * sggCdFromApi(5자리)를 앞자리로 갖는 활성 법정동코드 후보 중 읍면동명이 umdNm과 일치하는 행을 찾는다.
     * 후보가 여러 건이어도 읍면동명이 유일하면 정밀 매핑되고, 일치하는 행이 없으면 매칭 실패로 본다.
     */
    public Optional<String> matchByTradeSggCd(String sggCdFromApi, String umdNm) {
        List<LegalDistrictCode> candidates =
                legalDistrictCodeRepository.findByLegalDongCdStartingWithAndIsActiveTrue(sggCdFromApi);

        if (umdNm == null) {
            return Optional.empty();
        }
        String targetEupmyeondong = umdNm.trim();

        return candidates.stream()
                .filter(candidate -> targetEupmyeondong.equals(candidate.getEupmyeondongName()))
                .map(LegalDistrictCode::getLegalDongCd)
                .findFirst();
    }
}
