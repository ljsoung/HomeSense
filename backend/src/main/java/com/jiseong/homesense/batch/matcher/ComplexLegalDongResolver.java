package com.jiseong.homesense.batch.matcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.jiseong.homesense.region.entity.LegalDistrictCode;

/**
 * complex(단지 기본정보 xlsx 유래)의 주소 텍스트로 legal_district_code의 leaf 행(읍면동/리)을 찾는다.
 * complex.legal_dong_cd 백필({@link ComplexLegalDongBackfillService})과 향후 단지 적재 경로가 같은
 * 로직을 쓰도록 한 곳에 모았다 — 복사하지 말고 이 클래스를 재사용하라.
 *
 * <p>두 소스의 표기가 다르다. complex는 "수원장안구"(붙여쓰기, "시" 생략), dong_ri는 리 이름만
 * ("우두리")이고, legal_district_code는 "수원시 장안구", eupmyeondong_name은 "돌산읍 우두리"다.
 * 그래서 비교는 항상 legal_district_code 쪽을 {@link SigunguNormalizer}로 정규화해서 한다
 * ({@link ComplexMasterMatcher}의 1차 필터링과 같은 원칙).
 *
 * <p>두 가지 방식을 제공한다.
 * <ul>
 *   <li>{@link #byName}: (시도, 시군구, 동리) 이름 일치. 같은 시군구 안에 같은 이름의 리가 여러 읍면에
 *       있으면 주소에 그 읍면 이름이 들어 있는지로 좁히고, 그래도 여럿이면 매칭하지 않는다.</li>
 *   <li>{@link #byAddressPrefix}: legal_dong_address가 "시도 정규화시군구 읍면동[ 리]"로 시작하는
 *       가장 긴 leaf 행.</li>
 * </ul>
 * 인스턴스는 활성 leaf 행 목록으로 한 번 만들고 여러 단지에 재사용한다(메모리 인덱스).
 */
public final class ComplexLegalDongResolver {

    private final Map<String, List<LegalDistrictCode>> byNameKey = new HashMap<>();
    private final List<PrefixEntry> prefixEntries = new ArrayList<>();

    private record PrefixEntry(String prefix, LegalDistrictCode code) {
    }

    private ComplexLegalDongResolver(List<LegalDistrictCode> codes) {
        for (LegalDistrictCode code : codes) {
            if (!code.isActive() || code.getEupmyeondongName() == null) {
                continue;
            }
            String emd = code.getEupmyeondongName();
            String lastToken = emd.substring(emd.lastIndexOf(' ') + 1);
            byNameKey.computeIfAbsent(nameKey(code.getSidoName(), SigunguNormalizer.normalize(code.getSigunguName()),
                    lastToken), k -> new ArrayList<>()).add(code);
            prefixEntries.add(new PrefixEntry(addressPrefix(code), code));
        }
        prefixEntries.sort(Comparator.comparingInt((PrefixEntry e) -> e.prefix().length()).reversed());
    }

    /** 활성 행만 골라 쓴다 — 비활성 행이 섞여 들어와도 무시한다. */
    public static ComplexLegalDongResolver of(List<LegalDistrictCode> codes) {
        return new ComplexLegalDongResolver(codes);
    }

    /** 이름 일치를 먼저 시도하고, 실패하면 주소 prefix 일치로 보완한다. */
    public Optional<LegalDistrictCode> resolve(String sido, String sigungu, String dongRi, String address) {
        return byName(sido, sigungu, dongRi, address).or(() -> byAddressPrefix(address));
    }

    public Optional<LegalDistrictCode> byName(String sido, String sigungu, String dongRi, String address) {
        if (sido == null || dongRi == null) {
            return Optional.empty();
        }
        List<LegalDistrictCode> candidates = byNameKey.getOrDefault(nameKey(sido, sigungu, dongRi), List.of());
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }
        if (candidates.isEmpty() || address == null) {
            return Optional.empty();
        }
        String normalizedAddress = collapse(address);
        List<LegalDistrictCode> narrowed = candidates.stream()
                .filter(code -> normalizedAddress.contains(" " + code.getEupmyeondongName() + " "))
                .toList();
        return narrowed.size() == 1 ? Optional.of(narrowed.get(0)) : Optional.empty();
    }

    public Optional<LegalDistrictCode> byAddressPrefix(String address) {
        if (address == null) {
            return Optional.empty();
        }
        String normalizedAddress = collapse(address) + " ";
        return prefixEntries.stream()
                .filter(entry -> normalizedAddress.startsWith(entry.prefix() + " "))
                .map(PrefixEntry::code)
                .findFirst();
    }

    private static String addressPrefix(LegalDistrictCode code) {
        StringBuilder sb = new StringBuilder(code.getSidoName());
        String sigungu = SigunguNormalizer.normalize(code.getSigunguName());
        if (sigungu != null) {
            sb.append(' ').append(sigungu);
        }
        return sb.append(' ').append(code.getEupmyeondongName()).toString();
    }

    private static String nameKey(String sido, String sigungu, String dongRi) {
        return sido + "|" + Objects.toString(sigungu, "") + "|" + dongRi;
    }

    private static String collapse(String raw) {
        return raw.trim().replaceAll("\\s+", " ");
    }
}
