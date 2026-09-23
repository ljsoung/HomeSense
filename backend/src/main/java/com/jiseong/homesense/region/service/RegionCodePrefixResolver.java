package com.jiseong.homesense.region.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.jiseong.homesense.batch.matcher.LegalDistrictCodeReloadedEvent;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;

import lombok.RequiredArgsConstructor;

/**
 * 법정동코드(10자리)를 "그 지역과 하위 지역 전부"를 가리키는 코드 prefix로 바꾼다 — 단지 검색의
 * {@code complex.legal_dong_cd LIKE 'prefix%'} 계층 필터(API-CPX-01 regionCode)가 쓴다.
 *
 * <p>규칙(2026-09-23 활성 코드 전수 실증, CLAUDE.md "단지 검색 지역코드·키워드" 절):
 * <ul>
 *   <li>시도(뒤 8자리 0): 앞 2자리.</li>
 *   <li>시군구 대표행(뒤 5자리 0): 앞 5자리. 단, 구를 가진 시(수원시 41110 → 41111·41113…)는 구 코드가
 *       5번째 자리에서 갈라지므로 앞 4자리. 이 13개 시는 4자리 그룹 안에 자기와 산하 구만 있다.</li>
 *   <li>읍면동(뒤 2자리 0): 앞 8자리. 리: 10자리 전체.</li>
 * </ul>
 * "구를 가진 시"인지는 코드 숫자로 판정하면 안 된다 — 영동군(43740)과 증평군(43745)은 서로 무관한데
 * 같은 4자리 {@code 4374}를 공유한다. 그래서 같은 4자리 안에 {@code "{시군구명} "}으로 시작하는 하위
 * 시군구가 있는지(이름)로 판정한다. 세종(3611000000)은 시군구 계층이 없어 시군구 대표행 규칙으로 앞
 * 5자리({@code 36110})가 되고, 하위 동 전부가 그 prefix로 시작한다.
 *
 * <p>판정용 데이터는 요청마다 조회하지 않는다. 활성 코드 전체(약 2만 건)로 코드→prefix 맵을 한 번
 * 만들어 두고, 법정동코드 재적재 이벤트({@link LegalDistrictCodeReloadedEvent})를 받으면 비워 다음
 * 요청에서 다시 만든다.
 */
@Component
@RequiredArgsConstructor
public class RegionCodePrefixResolver {

    private final LegalDistrictCodeRepository legalDistrictCodeRepository;

    private volatile Map<String, String> prefixByCode;

    /** 존재하지 않거나 폐지된 코드는 empty — 호출자는 빈 결과로 처리한다. */
    public Optional<String> prefixOf(String legalDongCd) {
        Map<String, String> snapshot = prefixByCode;
        if (snapshot == null) {
            snapshot = load();
        }
        return Optional.ofNullable(snapshot.get(legalDongCd));
    }

    /**
     * 재적재 트랜잭션 커밋 뒤에 비운다 — 커밋 전에 비우면 그 사이 요청이 옛 데이터로 맵을 다시 만들 수
     * 있다(CacheEvictionListener와 같은 이유).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLegalDistrictCodeReloaded(LegalDistrictCodeReloadedEvent event) {
        prefixByCode = null;
    }

    private synchronized Map<String, String> load() {
        if (prefixByCode == null) {
            prefixByCode = computePrefixes(legalDistrictCodeRepository.findAll());
        }
        return prefixByCode;
    }

    /** 활성 코드만 대상으로 코드→prefix 맵을 만든다(단위 테스트용으로 분리). */
    static Map<String, String> computePrefixes(List<LegalDistrictCode> codes) {
        List<LegalDistrictCode> active = codes.stream().filter(LegalDistrictCode::isActive).toList();
        Map<String, String> result = new HashMap<>();
        for (LegalDistrictCode code : active) {
            result.put(code.getLegalDongCd(), prefixOf(code, active));
        }
        return result;
    }

    private static String prefixOf(LegalDistrictCode code, List<LegalDistrictCode> active) {
        String cd = code.getLegalDongCd();
        if (cd.endsWith("00000000")) {
            return cd.substring(0, 2);
        }
        if (cd.endsWith("00000")) {
            return hasChildGu(code, active) ? cd.substring(0, 4) : cd.substring(0, 5);
        }
        if (cd.endsWith("00")) {
            return cd.substring(0, 8);
        }
        return cd;
    }

    private static boolean hasChildGu(LegalDistrictCode city, List<LegalDistrictCode> active) {
        String name = city.getSigunguName();
        if (name == null || name.contains(" ")) {
            return false;
        }
        String group = city.getLegalDongCd().substring(0, 4);
        String childPrefix = name + " ";
        return active.stream().anyMatch(other -> other.getLegalDongCd().startsWith(group)
                && other.getSigunguName() != null
                && other.getSigunguName().startsWith(childPrefix));
    }
}
