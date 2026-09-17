package com.jiseong.homesense.batch.matcher;

/**
 * "시+구" 구조 도시(수원/성남/청주 등)에서 xlsx 유래 {@code complex.sigungu}(예: "수원장안구", 공백
 * 없음·"시" 생략)와 CSV 유래 {@code legal_district_code.sigungu_name}(예: "수원시 장안구", 공백 있음·
 * "시" 유지)의 표기가 달라 1차 필터링 후보가 항상 0건이 되던 문제를 해결한다(BAT-MAT-02 1차 필터링,
 * 실 DB로 확인). "시 구"처럼 원본에 공백이 있는 경우에만 공백을 제거하고 문자열 끝이 아닌 위치의
 * "시"를 제거한다. {@code legal_district_code} 쪽(CSV 유래) 값에만 적용하고 {@code complex.sigungu}
 * (xlsx 원본)는 절대 건드리지 않는다 — 두 원천 모두 정부 원본 표기를 그대로 DB에 보존해야 하므로
 * 정규화는 비교 시점에만 수행한다.
 *
 * <p>{@link ComplexMasterMatcher}(실제 1차 필터링)와 {@link RegionCoverageChecker}(재적재 후 커버리지
 * 경량 검증) 양쪽이 이 로직을 공유한다 — 두 곳이 각자 구현을 복제하면 한쪽만 고쳐지는 드리프트 위험이
 * 있다(2026년 법정동코드 재적재 세션에서 이 정규화가 정확히 이런 이유로 별도 클래스로 추출됐다).
 *
 * <p><b>원본에 공백이 없으면 절대 건드리지 않는다(2026-09-16 회귀 수정) — 이전 구현은 공백 유무를
 * 보지 않고 "문자열 끝이 아닌 위치의 시"를 무조건 지웠는데, "시흥시"처럼 도시 이름 자체가 "시"로
 * 시작하는 단일 시/군(구 분리 없음)에서 그 첫 글자를 "시+구" 분리자로 오인해 지워버려 "흥시"가
 * 됐다 — complex.sigungu="시흥시"(xlsx 원본, 불변)와 영원히 달라져 시흥시 소속 거래 전체(실측
 * 2,189건)가 1차 필터링 후보 0건으로 떨어졌다. "목포시"는 우연히 "시"가 마지막 글자라 이 버그를
 * 피해갔을 뿐, 같은 원인이었다. "시+구" 분리는 원본에 공백이 있을 때만(예: "수원시 장안구") 의미가
 * 있으므로, 공백이 없는 단일 시/군 표기는 무조건 원본 그대로 반환하도록 전제 조건을 추가했다 —
 * 이제 "시"가 몇 번 등장하든, 어느 위치에 있든 공백 없는 입력에는 영향을 주지 않는다.</b>
 */
final class SigunguNormalizer {

    private SigunguNormalizer() {
    }

    static String normalize(String raw) {
        if (raw == null || !raw.contains(" ")) {
            return raw;
        }
        String noSpace = raw.replaceAll("\\s+", "");
        return noSpace.replaceAll("시(?=.)", "");
    }
}
