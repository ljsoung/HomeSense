package com.jiseong.homesense.batch.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

class KakaoGeocodingClientTest {

    private static final String QUERY = "서울특별시 종로구 내수동 73";

    private MockRestServiceServer server;
    private KakaoGeocodingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoGeocodingClient(builder.build());
    }

    private ResponseActions expectRequest() {
        return server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/address.json?query=")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-key"))
                .andExpect(request -> assertThat(URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8))
                        .isEqualTo("query=" + QUERY));
    }

    @Test
    void 첫_결과의_y를_위도_x를_경도로_7자리에_맞춰_돌려준다() {
        expectRequest().andRespond(withSuccess("""
                {"documents":[{"address_name":"서울 종로구 내수동 73","x":"126.97227295596","y":"37.5726144754613"},
                              {"x":"1","y":"2"}],
                 "meta":{"total_count":2}}""", MediaType.APPLICATION_JSON));

        KakaoGeocodingClient.GeocodeResult result = client.geocode(QUERY, false);

        assertThat(result).isEqualTo(new KakaoGeocodingClient.Found(new BigDecimal("37.5726145"),
                new BigDecimal("126.9722730")));
    }

    @Test
    void documents가_비면_못_찾음이다() {
        expectRequest().andRespond(withSuccess(
                "{\"documents\":[],\"meta\":{\"total_count\":0}}", MediaType.APPLICATION_JSON));

        assertThat(client.geocode(QUERY, false)).isEqualTo(new KakaoGeocodingClient.NotFound("NO_RESULT"));
    }

    @Test
    void 다시_보내도_같을_4xx는_못_찾음이다() {
        expectRequest().andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.geocode(QUERY, false)).isEqualTo(new KakaoGeocodingClient.NotFound("HTTP_400"));
    }

    @Test
    void 요청_한도_초과_429는_재시도하지_않고_실행을_멈추게_한다() {
        expectRequest().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.geocode(QUERY, false)).isInstanceOf(GeocodingRateLimitedException.class);
    }

    @Test
    void 서버_오류_5xx는_재시도_대상이다() {
        expectRequest().andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.geocode(QUERY, false))
                .isInstanceOf(GeocodingTransientException.class)
                .hasMessage("HTTP_502");
    }

    @Test
    void 네트워크_오류는_재시도_대상이다() {
        expectRequest().andRespond(withException(new IOException("timeout")));

        assertThatThrownBy(() -> client.geocode(QUERY, false))
                .isInstanceOf(GeocodingTransientException.class)
                .hasMessageStartingWith("IO_");
    }

    @Test
    void 권한_없음_403도_설정_오류다() {
        expectRequest().andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.geocode(QUERY, false)).isInstanceOf(GeocodingConfigurationException.class);
    }

    @Test
    void 유일해야_하는데_결과가_여럿이면_못_찾음이다() {
        expectRequest().andRespond(withSuccess("""
                {"documents":[{"x":"127.37","y":"37.24"},{"x":"127.40","y":"37.26"}],"meta":{"total_count":2}}""",
                MediaType.APPLICATION_JSON));

        assertThat(client.geocode(QUERY, true)).isEqualTo(new KakaoGeocodingClient.NotFound("AMBIGUOUS"));
    }

    @Test
    void 유일해야_할_때_결과가_하나면_좌표를_돌려준다() {
        expectRequest().andRespond(withSuccess("""
                {"documents":[{"x":"127.595351141786","y":"34.9802645994355"}],"meta":{"total_count":1}}""",
                MediaType.APPLICATION_JSON));

        assertThat(client.geocode(QUERY, true)).isEqualTo(new KakaoGeocodingClient.Found(new BigDecimal("34.9802646"),
                new BigDecimal("127.5953511")));
    }

    @Test
    void 인증_실패는_설정_오류다() {
        expectRequest().andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.geocode(QUERY, false)).isInstanceOf(GeocodingConfigurationException.class);
    }
}
