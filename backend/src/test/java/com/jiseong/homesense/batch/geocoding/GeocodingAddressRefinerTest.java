package com.jiseong.homesense.batch.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * BAT-GEO-01 주소 정제. 실측 사례(2026-10-12 로컬 DB)를 그대로 입력으로 쓴다.
 */
class GeocodingAddressRefinerTest {

    @Test
    void 지번_뒤에_붙은_단지명을_버린다() {
        assertThat(GeocodingAddressRefiner.refine("서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동"))
                .map(GeocodingAddressRefiner.RefinedAddress::query).contains("서울특별시 종로구 내수동 72");
    }

    @Test
    void 부번_없이_하이픈만_남은_지번은_본번만_쓴다() {
        assertThat(GeocodingAddressRefiner.refine("서울특별시 종로구 내수동 73- 경희궁의아침4단지", "내수동"))
                .map(GeocodingAddressRefiner.RefinedAddress::query).contains("서울특별시 종로구 내수동 73");
    }

    @Test
    void 쉼표로_이어진_주소는_지번이_있는_첫_조각을_쓴다() {
        assertThat(GeocodingAddressRefiner.refine(
                "서울특별시 양천구 신월동 565-5,서울특별시 양천구 신월동  목동 센트럴아이파크위브", "신월동"))
                .map(GeocodingAddressRefiner.RefinedAddress::query).contains("서울특별시 양천구 신월동 565-5");
    }

    @Test
    void 첫_조각에_지번이_없으면_다음_조각을_본다() {
        assertThat(GeocodingAddressRefiner.refine(
                "경기도 남양주시 와부읍 덕소리  ,경기도 남양주시 와부읍 덕소리 619- 덕소강변신일해피트리아파트", "덕소리"))
                .map(GeocodingAddressRefiner.RefinedAddress::query).contains("경기도 남양주시 와부읍 덕소리 619");
    }

    @Test
    void 지번이_없으면_정제하지_않는다() {
        assertThat(GeocodingAddressRefiner.refine("서울특별시 중랑구 망우동  신내역 힐데스하임아파트", "망우동")).isEmpty();
    }

    @Test
    void 산_지번은_산을_붙여_남긴다() {
        assertThat(GeocodingAddressRefiner.refine("부산광역시 사하구 감천동 산 12-3 감천마을아파트", "감천동"))
                .map(GeocodingAddressRefiner.RefinedAddress::query).contains("부산광역시 사하구 감천동 산 12-3");
        assertThat(GeocodingAddressRefiner.refine("부산광역시 사하구 감천동 산12 감천마을아파트", "감천동"))
                .map(GeocodingAddressRefiner.RefinedAddress::query).contains("부산광역시 사하구 감천동 산 12");
    }

    @Test
    void 리_단위와_연속_공백을_정리한다() {
        assertThat(GeocodingAddressRefiner.refine("경상남도 창원의창구 동읍 용정리 20-3 성진2차", "용정리"))
                .map(GeocodingAddressRefiner.RefinedAddress::query).contains("경상남도 창원의창구 동읍 용정리 20-3");
        assertThat(GeocodingAddressRefiner.refine("세종특별자치시  고운동 1403 가락마을 14단지", "고운동"))
                .map(GeocodingAddressRefiner.RefinedAddress::query).contains("세종특별자치시 고운동 1403");
    }

    @Test
    void 본번_0은_지번으로_보지_않는다() {
        assertThat(GeocodingAddressRefiner.refine("울산광역시 울주군 청량읍 덕하리 0 뉴시티", "덕하리")).isEmpty();
    }

    @Test
    void 동리는_토큰_전체가_같을_때만_기준으로_삼는다() {
        // 단지명 "신월동아파트"의 앞부분을 동리로 잡지 않는다 — 실제 동리 토큰 뒤 지번을 쓴다
        assertThat(GeocodingAddressRefiner.refine("서울특별시 양천구 신월동 217-2 신월동아파트", "신월동"))
                .map(GeocodingAddressRefiner.RefinedAddress::query).contains("서울특별시 양천구 신월동 217-2");
        assertThat(GeocodingAddressRefiner.refine("서울특별시 양천구 신월동아파트 17", "신월동")).isEmpty();
    }

    @Test
    void 동리_경로는_첫_결과를_쓴다() {
        assertThat(GeocodingAddressRefiner.refine("서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동"))
                .map(GeocodingAddressRefiner.RefinedAddress::requireUnique).contains(false);
    }

    @Test
    void 주소가_없으면_정제하지_않는다() {
        assertThat(GeocodingAddressRefiner.refine(null, "내수동")).isEmpty();
    }

    // dong_ri가 NULL인 9건(로컬 실측 원문)

    @Test
    void 동리가_없으면_읍면_뒤_지번을_쓰되_결과가_하나일_때만_쓴다() {
        assertThat(GeocodingAddressRefiner.refine("전남광주통합특별시 광양시 광양읍  484-28 광양목성파크뷰 부영2단지", null))
                .contains(new GeocodingAddressRefiner.RefinedAddress("전남광주통합특별시 광양시 광양읍 484-28", true));
        assertThat(GeocodingAddressRefiner.refine("경기도 이천시 마장면  354 마장휴먼빌까사포레아파트", " "))
                .contains(new GeocodingAddressRefiner.RefinedAddress("경기도 이천시 마장면 354", true));
    }

    @Test
    void 동리가_없어도_뒤_조각에_리와_지번이_있으면_그것을_우선한다() {
        assertThat(GeocodingAddressRefiner.refine(
                "경기도 남양주시 와부읍  513,경기도 남양주시 와부읍 덕소리 619- 덕소강변신일해피트리아파트", null))
                .contains(new GeocodingAddressRefiner.RefinedAddress("경기도 남양주시 와부읍 덕소리 619", false));
        // 첫 조각의 본번 0은 지번이 아니다
        assertThat(GeocodingAddressRefiner.refine(
                "울산광역시 울주군 청량읍  0,울산광역시 울주군 청량읍 덕하리 2-,울산광역시 울주군 청량읍 덕하리 1101 뉴시티에일린의뜰2차",
                null))
                .contains(new GeocodingAddressRefiner.RefinedAddress("울산광역시 울주군 청량읍 덕하리 2", false));
    }

    @Test
    void 동리가_없고_지번도_없으면_정제하지_않는다() {
        assertThat(GeocodingAddressRefiner.refine("경상남도 양산시 동면   양산사송더샵데시앙2차5단지아파트", null)).isEmpty();
    }

    @Test
    void 동리가_없을_때_지번_뒤_단지명의_숫자를_행정구역으로_잡지_않는다() {
        // 513을 지나면 행정구역이 끝난 것으로 본다 — 단지명 "행복동 2단지"의 "행복동"을 기준으로 삼지 않는다
        assertThat(GeocodingAddressRefiner.refine("경기도 남양주시 와부읍 513 행복동 2단지", null))
                .contains(new GeocodingAddressRefiner.RefinedAddress("경기도 남양주시 와부읍 513", true));
    }
}
