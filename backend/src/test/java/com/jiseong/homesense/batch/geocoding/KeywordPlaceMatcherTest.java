package com.jiseong.homesense.batch.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.batch.geocoding.KakaoGeocodingClient.KeywordPlace;
import com.jiseong.homesense.batch.geocoding.KeywordPlaceMatcher.ComplexIdentity;
import com.jiseong.homesense.batch.geocoding.KeywordPlaceMatcher.Matched;
import com.jiseong.homesense.batch.geocoding.KeywordPlaceMatcher.NoMatch;

class KeywordPlaceMatcherTest {

    private static final String APARTMENT = "부동산 > 주거시설 > 아파트";

    private static KeywordPlace place(String name, String category, String address) {
        return new KeywordPlace(name, category, address, new BigDecimal("37.5000000"), new BigDecimal("127.0000000"));
    }

    private static final ComplexIdentity DAECHI = new ComplexIdentity("서울특별시", "강남구", "대치동", "대치푸르지오써밋");

    @Test
    void 조건을_통과한_후보가_하나면_채택한다() {
        KeywordPlace apartment = place("대치푸르지오써밋아파트", APARTMENT, "서울 강남구 대치동 1032");

        assertThat(KeywordPlaceMatcher.match(DAECHI, List.of(
                apartment,
                place("대치푸르지오써밋아파트 정문", "교통,수송 > 입출구", "서울 강남구 대치동 1032"),
                place("멜라 대치점", "음식점 > 양식", "서울 강남구 대치동 1032"))))
                .isEqualTo(new Matched(apartment));
    }

    @Test
    void 카테고리가_주거시설이나_아파트가_아니면_후보가_아니다() {
        assertThat(KeywordPlaceMatcher.match(DAECHI, List.of(
                place("대치푸르지오써밋아파트 관리사무소", "서비스,산업 > 관리,운영 > 건물관리사무소", "서울 강남구 대치동 1032"))))
                .isEqualTo(new NoMatch("KEYWORD_NO_CANDIDATE"));
    }

    @Test
    void 카테고리는_단계_단위로_비교해_아파트상가를_아파트로_보지_않는다() {
        assertThat(KeywordPlaceMatcher.isResidential("가정,생활 > 상가,아케이드 > 아파트상가")).isFalse();
        assertThat(KeywordPlaceMatcher.isResidential("부동산 > 주거시설 > 오피스텔")).isTrue();
        assertThat(KeywordPlaceMatcher.isResidential(APARTMENT)).isTrue();
        assertThat(KeywordPlaceMatcher.isResidential(null)).isFalse();
    }

    @Test
    void 다른_시군구의_같은_이름_단지는_후보가_아니다() {
        ComplexIdentity busanNamgu = new ComplexIdentity("부산광역시", "남구", "대연동", "푸르지오");

        assertThat(KeywordPlaceMatcher.match(busanNamgu, List.of(
                place("푸르지오아파트", APARTMENT, "서울 강남구 대치동 1"),
                place("푸르지오아파트", APARTMENT, "울산 남구 대연동 3"),
                place("푸르지오아파트", APARTMENT, "부산 남구 용호동 2"))))
                .isEqualTo(new NoMatch("KEYWORD_NO_CANDIDATE"));
    }

    @Test
    void 조건을_통과한_후보가_여럿이면_채택하지_않는다() {
        assertThat(KeywordPlaceMatcher.match(DAECHI, List.of(
                place("대치푸르지오써밋아파트", APARTMENT, "서울 강남구 대치동 1032"),
                place("대치푸르지오써밋아파트 2단지", APARTMENT, "서울 강남구 대치동 1033"))))
                .isEqualTo(new NoMatch("KEYWORD_AMBIGUOUS"));
    }

    @Test
    void 동리는_토큰이_같아야_한다() {
        ComplexIdentity sindong = new ComplexIdentity("경기도", "화성동탄구", "신동", "e편한세상동탄파크아너스");

        assertThat(KeywordPlaceMatcher.isInRegion(sindong, "경기 화성시 동탄구 신동 815")).isTrue();
        assertThat(KeywordPlaceMatcher.isInRegion(sindong, "경기 화성시 동탄구 신동탄동 1")).isFalse();
    }

    @Test
    void 시도는_카카오_약칭과_원래_이름을_모두_받는다() {
        assertThat(KeywordPlaceMatcher.isInRegion(DAECHI, "서울 강남구 대치동 1032")).isTrue();
        assertThat(KeywordPlaceMatcher.isInRegion(DAECHI, "서울특별시 강남구 대치동 1032")).isTrue();
        assertThat(KeywordPlaceMatcher.isInRegion(DAECHI, "경기 강남구 대치동 1032")).isFalse();
        ComplexIdentity jeonnam = new ComplexIdentity("전남광주통합특별시", "목포시", "용당동", "용당아파트");
        assertThat(KeywordPlaceMatcher.isInRegion(jeonnam, "전남광주통합특별시 목포시 용당동 982-25")).isTrue();
    }

    @Test
    void 구가_있는_시는_카카오의_두_토큰을_K_apt_표기로_맞춘다() {
        ComplexIdentity changwon = new ComplexIdentity("경상남도", "창원의창구", "용정리", "성진2차");

        assertThat(KeywordPlaceMatcher.isInRegion(changwon, "경남 창원시 의창구 동읍 용정리 20-3")).isTrue();
        assertThat(KeywordPlaceMatcher.isInRegion(changwon, "경남 창원시 성산구 동읍 용정리 20-3")).isFalse();
    }

    @Test
    void 시군구가_없는_세종은_시도_뒤에서_동리를_찾는다() {
        ComplexIdentity sejong = new ComplexIdentity("세종특별자치시", null, "소담동", "새샘마을3단지 아파트");

        assertThat(KeywordPlaceMatcher.isInRegion(sejong, "세종특별자치시 소담동 550")).isTrue();
        assertThat(KeywordPlaceMatcher.queries(sejong))
                .containsExactly("세종특별자치시 새샘마을3단지 아파트", "세종특별자치시 새샘마을3단지");
    }

    @Test
    void 단지명이_아파트로_끝날_때만_뗀_검색어를_더한다() {
        assertThat(KeywordPlaceMatcher.queries(new ComplexIdentity("서울특별시", "중구", "신당동", "신당동아파트")))
                .containsExactly("중구 신당동아파트", "중구 신당동");
        assertThat(KeywordPlaceMatcher.queries(DAECHI)).containsExactly("강남구 대치푸르지오써밋");
        assertThat(KeywordPlaceMatcher.queries(new ComplexIdentity("서울특별시", "중구", "신당동", "아파트")))
                .containsExactly("중구 아파트");
    }

    @Test
    void 동리를_모르면_지역을_확인할_수_없어_후보가_아니다() {
        ComplexIdentity noDong = new ComplexIdentity("서울특별시", "강남구", null, "대치푸르지오써밋");

        assertThat(KeywordPlaceMatcher.isInRegion(noDong, "서울 강남구 대치동 1032")).isFalse();
    }
}
