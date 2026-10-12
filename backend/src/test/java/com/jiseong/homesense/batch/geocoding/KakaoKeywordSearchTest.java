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
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import com.jiseong.homesense.batch.geocoding.KakaoGeocodingClient.KeywordPlace;
import com.jiseong.homesense.batch.geocoding.KeywordPlaceMatcher.ComplexIdentity;
import com.jiseong.homesense.batch.geocoding.KeywordPlaceMatcher.Matched;

/**
 * BAT-GEO-01 키워드 보완 경로. 응답 fixture(src/test/resources/geocoding/keyword-*.json)는 2026-10-12 실제 카카오 응답에서
 * 쓰는 필드만 남긴 것이다 — 주소 검색이 못 찾은 재건축 단지 3건(K-apt는 사업 전 지번을 갖고 있다).
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
        return server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/keyword.json?query=")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertThat(URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8))
                        .isEqualTo("query=" + query));
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = KakaoKeywordSearchTest.class.getResourceAsStream("/geocoding/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private KeywordPlace searchAndMatch(ComplexIdentity complex, String fixture) throws IOException {
        String query = KeywordPlaceMatcher.queries(complex).get(0);
        expectKeyword(query).andRespond(withSuccess(fixture(fixture), MediaType.APPLICATION_JSON));
        List<KeywordPlace> places = client.searchKeyword(query);
        KeywordPlaceMatcher.Result result = KeywordPlaceMatcher.match(complex, places);
        assertThat(result).isInstanceOf(Matched.class);
        return ((Matched) result).place();
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
    void 힐스테이트청계는_상가_관리사무소와_다른_구의_오피스텔을_거른다() throws IOException {
        KeywordPlace place = searchAndMatch(
                new ComplexIdentity("서울특별시", "동대문구", "답십리동", "힐스테이트청계"), "keyword-hillstate-cheonggye.json");

        assertThat(place.placeName()).isEqualTo("힐스테이트청계아파트");
        assertThat(place.addressName()).isEqualTo("서울 동대문구 답십리동 1009");
    }

    @Test
    void 더비치푸르지오써밋은_아파트상가를_두_번째_후보로_세지_않는다() throws IOException {
        KeywordPlace place = searchAndMatch(
                new ComplexIdentity("부산광역시", "남구", "대연동", "더비치푸르지오써밋"), "keyword-the-beach-prugio-summit.json");

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

        assertThat(client.searchKeyword("강남구 단지")).extracting(KeywordPlace::placeName).containsExactly("B");
    }

    @Test
    void 다시_보내도_같을_4xx면_빈_목록이다() {
        expectKeyword("강남구 단지").andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.searchKeyword("강남구 단지")).isEmpty();
    }

    @Test
    void 키워드_검색도_429는_실행_중단_5xx는_재시도_401_403은_설정_오류다() {
        expectKeyword("q").andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        expectKeyword("q").andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        expectKeyword("q").andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.searchKeyword("q")).isInstanceOf(GeocodingRateLimitedException.class);
        assertThatThrownBy(() -> client.searchKeyword("q")).isInstanceOf(GeocodingTransientException.class);
        assertThatThrownBy(() -> client.searchKeyword("q")).isInstanceOf(GeocodingConfigurationException.class);
    }
}
