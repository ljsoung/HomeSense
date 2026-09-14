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
import org.springframework.http.HttpStatusCode;
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
                // 서비스키 미등록/만료(resultCode 30/31) 같은 게이트웨이 레벨 오류는 data.go.kr이
                // 200 OK + XML 본문이 아니라 HTTP 4xx 상태로 내려준다 — RestClient의 기본 동작은
                // 4xx/5xx에서 body()를 반환하기 전에 HttpClientErrorException을 던지므로,
                // RealEstateApiCollector.requestPage()가 그 본문(OpenApiXmlReader가 읽어야 할
                // returnReasonCode)을 아예 받지 못하고 예외만 받는다. 이 예외는
                // BatchExecutionOrchestrator의 RestClientException catch절(일시적 전송 계층 실패)로
                // 흘러들어가 RETRY로 오분류되고, 즉시 ABORT_BATCH돼야 할 서비스키 오류가 조합마다
                // 1→5→30분 블로킹 재시도를 반복하게 된다(실제로 이 문제로 배치가 3시간 가까이
                // 멈춘 것처럼 보였다). 모든 상태 코드에서 예외 없이 본문을 그대로 반환하도록 기본
                // 상태 핸들러를 무력화해, resultCode/returnReasonCode 기반 판정(ApiErrorCodeClassifier)이
                // HTTP 상태와 무관하게 항상 실행되게 한다.
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .build();
    }
}
