package com.jiseong.homesense.batch.notifier;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 거래의 법정동코드(10자리)에서 그 거래를 포함할 수 있는 관심 지역 코드 후보를 펼친다(BAT-NTF-01 D5).
 * 리(里) 거래가 읍·면 관심 지역에, 동 거래가 시군구 관심 지역에 잡히도록 상위 코드를 모두 낸다.
 *
 * <p>후보: 자신, 읍면동(앞 8자리+00), 시군구(앞 5자리+00000), 구를 가진 시(앞 4자리+000000),
 * 시도(앞 2자리+00000000). 존재하지 않는 코드(예: 구가 없는 시의 4자리 후보)가 섞여도 무해하다 —
 * {@code favorite_region.legal_dong_cd IN (...)}의 후보로만 쓰여 해당 행이 없으면 걸리지 않는다.
 * 실제로 그 관심 지역이 이 거래를 포함하는지는 {@code RegionCodePrefixResolver}의 prefix로 다시 집계한다.
 */
final class LegalDongHierarchy {

    private static final Pattern CODE = Pattern.compile("\\d{10}");

    private LegalDongHierarchy() {
    }

    static Set<String> ancestorsAndSelf(String legalDongCd) {
        if (legalDongCd == null || !CODE.matcher(legalDongCd).matches()) {
            return Set.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        codes.add(legalDongCd);
        codes.add(legalDongCd.substring(0, 8) + "00");
        codes.add(legalDongCd.substring(0, 5) + "00000");
        codes.add(legalDongCd.substring(0, 4) + "000000");
        codes.add(legalDongCd.substring(0, 2) + "00000000");
        return codes;
    }

    static Set<String> ancestorsAndSelf(Iterable<String> legalDongCds) {
        Set<String> codes = new LinkedHashSet<>();
        for (String code : legalDongCds) {
            codes.addAll(ancestorsAndSelf(code));
        }
        return codes;
    }
}
