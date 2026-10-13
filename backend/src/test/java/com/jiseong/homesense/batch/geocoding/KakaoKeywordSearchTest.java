package com.jiseong.homesense.batch.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import com.jiseong.homesense.batch.geocoding.KakaoGeocodingClient.KeywordPage;
import com.jiseong.homesense.batch.geocoding.KakaoGeocodingClient.KeywordPlace;
import com.jiseong.homesense.batch.geocoding.KeywordPlaceMatcher.ComplexIdentity;
import com.jiseong.homesense.batch.geocoding.KeywordPlaceMatcher.Matched;

/**
 * BAT-GEO-01 키워드 보완 경로. 응답 fixture(src/test/resources/geocoding/keyword-*.json)는 2026-10-12 실제 카카오 응답에서
 * 쓰는 필드만 남긴 것이다 — 주소 검색이 못 찾은 재건축 단지 3건(K-apt는 사업 전 지번을 갖고 있다). 첫 페이지가
 * is_end=false인 두 단지는 같은 날 받은 2·3쪽(*-page2/3.json)까지 둔다.
 */
class KakaoKeywordSearchTest {

    private MockRestServiceServer server;
    private KakaoGeocodingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoGeocodingClient(builder.build());
    }

    private ResponseActions expectKeyword(String query) {
        return expectKeyword(query, 1);
    }

    private ResponseActions expectKeyword(String query, int page) {
        return server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/keyword.json?query=")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertThat(URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8))
                        .isEqualTo("query=" + query + "&page=" + page + "&size=15"));
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = KakaoKeywordSearchTest.class.getResourceAsStream("/geocoding/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 서비스처럼 마지막 페이지(is_end)까지 받아 모은 뒤 판정한다. fixtures는 1쪽부터 순서대로이고, 마지막 fixture가 is_end여야
     * 한다(아니면 서버가 기대하지 않은 요청으로 실패한다).
     */
    private KeywordPlaceMatcher.Result search(ComplexIdentity complex, String query, String... fixtures) throws IOException {
        for (int i = 0; i < fixtures.length; i++) {
            expectKeyword(query, i + 1).andRespond(withSuccess(fixture(fixtures[i]), MediaType.APPLICATION_JSON));
        }
        List<KeywordPlace> places = new ArrayList<>();
        for (int page = 1; ; page++) {
            KeywordPage result = client.searchKeyword(query, page);
            places.addAll(result.places());
            if (result.isEnd()) {
                break;
            }
        }
        server.verify();
        return KeywordPlaceMatcher.match(complex, places);
    }

    private KeywordPlace searchAndMatch(ComplexIdentity complex, String... fixtures) throws IOException {
        KeywordPlaceMatcher.Result result = search(complex, KeywordPlaceMatcher.queries(complex).get(0), fixtures);
        assertThat(result).isInstanceOf(Matched.class);
        return ((Matched) result).place();
    }

    @Test
    void 동탄파크자이는_후보가_동탄역자이_하나뿐이라도_이름이_달라_탈락한다() throws IOException {
        ComplexIdentity complex = new ComplexIdentity("경기도", "화성동탄구", "영천동", "동탄파크자이아파트");

        assertThat(search(complex, "화성동탄구 동탄파크자이아파트", "keyword-dongtan-park-xi.json"))
                .isEqualTo(new KeywordPlaceMatcher.NoMatch("KEYWORD_NAME_MISMATCH"));
    }

    @Test
    void 감전엘에이치는_표기_차이를_맞춰_감전LH아파트를_채택한다() throws IOException {
        KeywordPlace place = searchAndMatch(
                new ComplexIdentity("부산광역시", "사상구", "감전동", "감전엘에이치"), "keyword-gamjeon-lh.json");

        assertThat(place.placeName()).isEqualTo("감전LH아파트");
        assertThat(place.addressName()).isEqualTo("부산 사상구 감전동 961");
    }

    @Test
    void 역삼자이는_같은_동의_역삼센트럴자이를_두고_정확히_같은_이름을_고른다() throws IOException {
        KeywordPlace place = searchAndMatch(
                new ComplexIdentity("서울특별시", "강남구", "역삼동", "역삼자이아파트"), "keyword-yeoksam-xi.json");

        assertThat(place.placeName()).isEqualTo("역삼자이아파트");
        assertThat(place.addressName()).isEqualTo("서울 강남구 역삼동 711-3");
    }

    @Test
    void 괄호를_지운_검색어로_왕십리텐즈힐1단지를_찾는다() throws IOException {
        ComplexIdentity complex = new ComplexIdentity("서울특별시", "성동구", "하왕십리동", "왕십리텐즈힐1단지(임대)");
        String parenthesisRemoved = KeywordPlaceMatcher.queries(complex).get(1);

        KeywordPlaceMatcher.Result result = search(complex, parenthesisRemoved, "keyword-wangsimni-tenshill-1.json");

        assertThat(parenthesisRemoved).isEqualTo("성동구 왕십리텐즈힐1단지");
        assertThat(result).isInstanceOf(Matched.class);
        assertThat(((Matched) result).place().placeName()).isEqualTo("텐즈힐1단지아파트");
    }

    @Test
    void 대치푸르지오써밋은_아파트_한_건만_채택한다() throws IOException {
        KeywordPlace place = searchAndMatch(
                new ComplexIdentity("서울특별시", "강남구", "대치동", "대치푸르지오써밋"), "keyword-daechi-prugio-summit.json");

        assertThat(place.placeName()).isEqualTo("대치푸르지오써밋아파트");
        assertThat(place.addressName()).isEqualTo("서울 강남구 대치동 1032");
        assertThat(place.latitude()).isEqualByComparingTo("37.5014784");
        assertThat(place.longitude()).isEqualByComparingTo("127.0617312");
    }

    @Test
    void 힐스테이트청계는_세_페이지를_모두_보고_상가_관리사무소와_다른_구의_오피스텔을_거른다() throws IOException {
        KeywordPlace place = searchAndMatch(
                new ComplexIdentity("서울특별시", "동대문구", "답십리동", "힐스테이트청계"), "keyword-hillstate-cheonggye.json",
                "keyword-hillstate-cheonggye-page2.json", "keyword-hillstate-cheonggye-page3.json");

        assertThat(place.placeName()).isEqualTo("힐스테이트청계아파트");
        assertThat(place.addressName()).isEqualTo("서울 동대문구 답십리동 1009");
    }

    @Test
    void 더비치푸르지오써밋은_세_페이지를_모두_보고_아파트상가를_두_번째_후보로_세지_않는다() throws IOException {
        KeywordPlace place = searchAndMatch(
                new ComplexIdentity("부산광역시", "남구", "대연동", "더비치푸르지오써밋"),
                "keyword-the-beach-prugio-summit.json", "keyword-the-beach-prugio-summit-page2.json",
                "keyword-the-beach-prugio-summit-page3.json");

        assertThat(place.placeName()).isEqualTo("더비치푸르지오써밋아파트");
        assertThat(place.addressName()).isEqualTo("부산 남구 대연동 1946");
    }

    @Test
    void 좌표가_숫자가_아닌_항목은_버린다() {
        expectKeyword("강남구 단지").andRespond(withSuccess("""
                {"documents":[{"place_name":"A","category_name":"부동산 > 주거시설 > 아파트","address_name":"서울 강남구 대치동 1",
                               "x":"","y":"37.5"},
                              {"place_name":"B","category_name":"부동산 > 주거시설 > 아파트","address_name":"서울 강남구 대치동 2",
                               "x":"127.0","y":"37.5"}]}""", MediaType.APPLICATION_JSON));

        assertThat(client.searchKeyword("강남구 단지", 1).places()).extracting(KeywordPlace::placeName).containsExactly("B");
    }

    @Test
    void 카카오_meta의_is_end로_다음_페이지가_있는지_알려_준다() throws IOException {
        expectKeyword("동대문구 힐스테이트청계", 1)
                .andRespond(withSuccess(fixture("keyword-hillstate-cheonggye.json"), MediaType.APPLICATION_JSON));
        expectKeyword("동대문구 힐스테이트청계", 3)
                .andRespond(withSuccess(fixture("keyword-hillstate-cheonggye-page3.json"), MediaType.APPLICATION_JSON));
        expectKeyword("q").andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        KeywordPage first = client.searchKeyword("동대문구 힐스테이트청계", 1);
        KeywordPage last = client.searchKeyword("동대문구 힐스테이트청계", 3);
        KeywordPage noMeta = client.searchKeyword("q", 1);

        assertThat(first.isEnd()).isFalse();
        assertThat(first.places()).hasSize(15);
        assertThat(last.isEnd()).isTrue();
        assertThat(noMeta.isEnd()).isTrue(); // meta가 없으면 다음 페이지를 알 수 없어 끝으로 본다
    }

    @Test
    void 다시_보내도_같을_4xx면_빈_목록이다() {
        expectKeyword("강남구 단지").andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.searchKeyword("강남구 단지", 1)).isEqualTo(new KeywordPage(List.of(), true));
    }

    @Test
    void 키워드_검색도_429는_실행_중단_5xx는_재시도_401_403은_설정_오류다() {
        expectKeyword("q").andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        expectKeyword("q").andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        expectKeyword("q").andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.searchKeyword("q", 1)).isInstanceOf(GeocodingRateLimitedException.class);
        assertThatThrownBy(() -> client.searchKeyword("q", 1)).isInstanceOf(GeocodingTransientException.class);
        assertThatThrownBy(() -> client.searchKeyword("q", 1)).isInstanceOf(GeocodingConfigurationException.class);
    }
}
