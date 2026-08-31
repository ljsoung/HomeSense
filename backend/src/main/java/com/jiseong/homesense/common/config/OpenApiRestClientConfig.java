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
