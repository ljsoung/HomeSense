package com.jiseong.homesense.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.jiseong.homesense.batch.geocoding.KakaoGeocodingClient;

/**
 * BAT-GEO-01이 쓰는 카카오 주소 검색 클라이언트와 설정을 등록한다. KakaoProperties는 소비자가 없던 동안
 * ExternalApiConfig에 임시로 등록돼 있었는데, 지오코딩이 생기면서 여기로 옮기고 그 클래스는 지웠다.
 *
 * <p>RestClient를 빈으로 노출하지 않고 클라이언트 안에만 둔다 — RestClient 빈이 둘이 되면 타입으로 주입받는
 * RealEstateApiCollector(BAT-CLC-01)의 주입이 모호해진다.
 *
 * <p>HTTP 상태 판단(4xx·429·5xx 구분)은 RestClient의 상태 핸들러가 아니라 KakaoGeocodingClient가
 * {@code exchange()}로 직접 한다 — OpenApiRestClientConfig와 같은 구성이다.
 */
@Configuration
@EnableConfigurationProperties({KakaoProperties.class, GeocodingProperties.class})
public class GeocodingConfig {

    static final String KAKAO_API_BASE_URL = "https://dapi.kakao.com";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public KakaoGeocodingClient kakaoGeocodingClient(KakaoProperties kakaoProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new KakaoGeocodingClient(RestClient.builder()
                .baseUrl(KAKAO_API_BASE_URL)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + kakaoProperties.apiKey())
                .build());
    }
}
