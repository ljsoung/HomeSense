package com.jiseong.homesense.batch.geocoding;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.jiseong.homesense.batch.matcher.JibunExtractor;

/**
 * BAT-GEO-01 주소 정제. complex.legal_dong_address(원본 xlsx "법정동주소" 그대로)를 카카오 주소 검색에 넣을
 * "시도 시군구 동리 (산)본번(-부번)" 문자열로 바꾼다. 원본은 그대로 넣으면 안 된다(2026-10-12 로컬 DB 실측).
 * <ul>
 *   <li>뒤에 단지명이 붙어 있다 — "서울특별시 종로구 내수동 72 경희궁의아침3단지"</li>
 *   <li>부번 없이 하이픈만 남은 경우가 있다(1,014건) — "서울특별시 종로구 내수동 73- 경희궁의아침4단지"</li>
 *   <li>쉼표로 주소 여러 개가 이어진다(1,374건) — "서울특별시 양천구 신월동 565-5,서울특별시 양천구 신월동  목동 센트럴..."</li>
 *   <li>지번이 아예 없다(약 1.2%, 지번 미부여 신규 단지) — "서울특별시 중랑구 망우동  신내역 힐데스하임아파트"</li>
 * </ul>
 * 쉼표로 나눈 조각 중 지번이 있는 첫 조각에서, 동리 토큰 바로 뒤의 지번까지만 남긴다(지번 추출은 BAT-MAT-02와 같은
 * {@link JibunExtractor}). 지번을 못 뽑으면 빈 값을 돌려주고 호출자는 지오코딩을 시도하지 않는다. dong_ri가 NULL인
 * 단지는 행정구역 토큰 바로 뒤의 지번으로 처리한다({@link #refineWithoutDongRi}).
 *
 * <p>시군구는 원본 표기("화성만세구", "창원의창구")를 그대로 둔다 — 카카오가 이 표기와 2026년 신설 행정구역
 * ("전남광주통합특별시", "제물포구")을 모두 정확히 찾는 것을 실제 호출로 확인했다(2026-10-12).
 */
public final class GeocodingAddressRefiner {

    private GeocodingAddressRefiner() {
    }

    /**
     * 정제 결과.
     *
     * @param query         카카오에 보낼 주소
     * @param requireUnique 결과가 정확히 하나일 때만 좌표를 쓴다. 지번은 리 단위로 매겨지므로 리가 빠진 "읍·면 + 지번"은
     *                      여러 리의 같은 지번에 걸릴 수 있다(실측: "경기도 이천시 마장면 354" → 9건)
     */
    public record RefinedAddress(String query, boolean requireUnique) {
    }

    /**
     * @param legalDongAddress complex.legal_dong_address
     * @param dongRi           complex.dong_ri — 주소 안에서 동리가 끝나는 위치를 찾는 기준. NULL이면(9건, 대부분 쉼표
     *                         다중 주소) {@link #refineWithoutDongRi}로 행정구역 토큰 바로 뒤의 지번을 찾는다
     */
    public static Optional<RefinedAddress> refine(String legalDongAddress, String dongRi) {
        if (legalDongAddress == null) {
            return Optional.empty();
        }
        if (dongRi == null || dongRi.isBlank()) {
            return refineWithoutDongRi(legalDongAddress);
        }
        for (String piece : legalDongAddress.split(",")) {
            Optional<String> refined = refinePiece(piece, dongRi.trim());
            if (refined.isPresent()) {
                return Optional.of(new RefinedAddress(refined.get(), false));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> refinePiece(String piece, String dongRi) {
        List<String> tokens = tokens(piece);
        for (int i = 0; i < tokens.size() - 1; i++) {
            // 동리는 토큰 전체가 같을 때만 기준으로 삼는다 — 단지명 안의 같은 글자("신월동 신월동아파트")를 잘못 집지 않는다.
            if (!tokens.get(i).equals(dongRi)) {
                continue;
            }
            Optional<JibunExtractor.Jibun> jibun = jibunAfter(tokens, i);
            if (jibun.isPresent()) {
                return Optional.of(String.join(" ", tokens.subList(0, i + 1)) + " " + jibun.get().display());
            }
        }
        return Optional.empty();
    }

    /**
     * dong_ri가 NULL인 단지(9건). 각 조각에서 숫자로 시작하는 토큰이 나오기 전의 행정구역 토큰 중, 바로 뒤에 지번이 오는
     * 토큰을 기준으로 삼는다. 동·리·가로 끝나는 기준이 있으면 그것을 쓰고(동리 경로와 같은 정밀도), 없으면 읍·면 기준을
     * 쓰되 결과가 하나일 때만 좌표를 쓴다. 예: "청량읍  0,…청량읍 덕하리 2-,…" → 덕하리 2,
     * "광양시 광양읍  484-28 …" → 광양읍 484-28(유일한 결과일 때만).
     */
    private static Optional<RefinedAddress> refineWithoutDongRi(String legalDongAddress) {
        String fallback = null;
        for (String piece : legalDongAddress.split(",")) {
            List<String> tokens = tokens(piece);
            // 0번(시도)은 기준이 될 수 없다. 숫자로 시작하는 토큰을 지나면 행정구역이 끝난 것이다.
            for (int i = 1; i < tokens.size() - 1 && !startsWithDigit(tokens.get(i)); i++) {
                Optional<JibunExtractor.Jibun> jibun = jibunAfter(tokens, i);
                if (jibun.isEmpty()) {
                    continue;
                }
                String query = String.join(" ", tokens.subList(0, i + 1)) + " " + jibun.get().display();
                if (isDongLevel(tokens.get(i))) {
                    return Optional.of(new RefinedAddress(query, false));
                }
                if (fallback == null) {
                    fallback = query;
                }
                break;
            }
        }
        return Optional.ofNullable(fallback).map(query -> new RefinedAddress(query, true));
    }

    private static List<String> tokens(String piece) {
        return Arrays.stream(piece.trim().split("\\s+")).filter(t -> !t.isEmpty()).toList();
    }

    private static Optional<JibunExtractor.Jibun> jibunAfter(List<String> tokens, int index) {
        String rest = String.join(" ", tokens.subList(index + 1, tokens.size()));
        return JibunExtractor.parseLeading(rest).filter(GeocodingAddressRefiner::hasMainNumber);
    }

    private static boolean startsWithDigit(String token) {
        return Character.isDigit(token.charAt(0));
    }

    /** "파장동", "덕하리", "양평동3가"처럼 지번이 매겨지는 단위의 이름인가. */
    private static boolean isDongLevel(String token) {
        return token.endsWith("동") || token.endsWith("리") || token.endsWith("가");
    }

    /** 본번 0("청량읍  0,…")은 원본의 자리채움 값이라 지번으로 보지 않는다. */
    private static boolean hasMainNumber(JibunExtractor.Jibun jibun) {
        String main = jibun.number().split("-", 2)[0];
        return !main.chars().allMatch(c -> c == '0');
    }
}
