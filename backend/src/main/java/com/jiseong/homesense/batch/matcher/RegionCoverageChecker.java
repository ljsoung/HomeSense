package com.jiseong.homesense.batch.matcher;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 재발 방지용 경량 커버리지 체크 — complex 마스터(xlsx 유래)가 실제로 쓰는 (시도, 시군구) 조합 중,
 * 현재 {@code legal_district_code}의 활성 코드로는 매칭 후보조차 찾을 수 없는 조합이 있는지 대조한다.
 *
 * <p>2026년 두 차례 행정구역 개편(화성시 일반구 신설, 인천 행정체제 개편·전남광주통합특별시 출범) 때
 * 이 공백이 몇 달간 감지되지 않고 방치됐다 — legal_district_code 참조자료가 노후화돼 BAT-SCH-01의
 * 조합 순회 목록({@code findDistinctActiveSggCd()})에서 아예 빠지면, 해당 지역 실거래는 원시 수집
 * 단계(BAT-CLC-01)부터 0건이 되고 어떤 예외도 던져지지 않아(result_code 000, "정상"으로 위장된 데이터
 * 없음) 아무 로그에도 잡히지 않는다. 이 체크는 "정교한 설계"가 아니라 그 최소 신호(로그 경고) 하나를
 * 남기는 것이 목적이다 — 완벽한 탐지가 아니라 다음 재발 시 몇 달이 아니라 재적재 직후 발견되게 하는 것.
 *
 * <p>정규화는 {@link SigunguNormalizer}를 {@link ComplexMasterMatcher}와 공유한다 — 실제 1차 필터링이
 * 쓰는 것과 다른 정규화 규칙으로 이 체크를 만들면, 이 체크가 "정상"이라고 판단해도 실제 매칭은 여전히
 * 실패하는(또는 그 반대) 자기모순적인 안전장치가 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionCoverageChecker {

    private final ComplexRepository complexRepository;
    private final LegalDistrictCodeRepository legalDistrictCodeRepository;

    /**
     * @return 매칭 후보를 찾을 수 없는 (시도, 시군구) 조합의 개수 — 호출자가 심각도를 판단할 때 쓴다.
     */
    public int checkCoverage() {
        Set<SidoSigungu> coveredByLegalDistrictCode = new HashSet<>();
        for (Object[] row : legalDistrictCodeRepository.findDistinctActiveSidoSigunguPairs()) {
            String sido = (String) row[0];
            String sigungu = SigunguNormalizer.normalize((String) row[1]);
            coveredByLegalDistrictCode.add(new SidoSigungu(sido, sigungu));
        }

        List<SidoSigungu> uncovered = complexRepository.findDistinctSidoSigunguPairs().stream()
                .map(row -> new SidoSigungu((String) row[0], (String) row[1]))
                .filter(pair -> !coveredByLegalDistrictCode.contains(pair))
                .toList();

        if (uncovered.isEmpty()) {
            log.info("BAT-MAT-01 지역 커버리지 체크 완료: complex 마스터의 모든 (시도, 시군구) 조합이 "
                    + "legal_district_code 활성 코드로 커버된다.");
            return 0;
        }

        log.warn("BAT-MAT-01 지역 커버리지 공백 발견 — complex 마스터에 존재하지만 legal_district_code "
                + "활성 코드로는 후보를 찾을 수 없는 (시도, 시군구) 조합 {}건: {}. 해당 지역 실거래는 "
                + "BAT-CLC-01 원시 수집 단계부터 누락될 수 있다 — 법정동코드 참조자료 최신화가 필요한지 확인하라.",
                uncovered.size(), uncovered);
        return uncovered.size();
    }

    private record SidoSigungu(String sido, String sigungu) {
    }
}
