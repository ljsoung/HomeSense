package com.jiseong.homesense.common.config;

import java.time.Duration;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * BAT-CLC-01(RealEstateApiCollector)이 쓰는 국토부 Open API 전용 RestClient를 구성한다.
 * 평균 응답 500ms/30TPS 제약(요구사항정의서 7.1절)을 고려해 타임아웃과 커넥션 풀을 둔다.
 *
 * <p>HTTP 상태 코드에 따른 처리(게이트웨이 오류 봉투 판정, 일시적 전송 계층 실패의 재시도 경로
 * 보존)는 이 빈 레벨의 {@code defaultStatusHandler}가 아니라 유일한 소비자인
 * {@code RealEstateApiCollector.requestPage()}가 {@code exchange()}로 응답 본문을 정확히 한 번만
 * 읽어 직접 판단한다 — 본문을 먼저 들여다본 뒤 그대로 통과시키려면 같은 스트림을 다시 읽어야
 * 하는데, 운영 HTTP 클라이언트(Apache HttpClient)든 테스트의 MockRestServiceServer든 응답
 * 스트림은 기본적으로 한 번만 읽을 수 있어 이 지점에서 재구성하지 않는다.
 */
@Configuration
@EnableConfigurationProperties(DataGoKrProperties.class)
public class OpenApiRestClientConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_TOTAL_CONNECTIONS = 20;
    private static final int MAX_CONNECTIONS_PER_ROUTE = 20;

    @Bean
    public RestClient openApiRestClient() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(MAX_TOTAL_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(MAX_CONNECTIONS_PER_ROUTE);
        // 연결 자체의 타임아웃(커넥션 풀에서 소켓을 맺는 시간)은 5.x부터 RequestConfig가 아니라
        // 커넥션 매니저의 ConnectionConfig로 옮겨졌다 — RequestConfig#setConnectTimeout는 deprecated.
        connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(TIMEOUT))
                .setSocketTimeout(Timeout.of(TIMEOUT))
                .build());

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.of(TIMEOUT))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }
}
