package com.jiseong.homesense.batch.collector;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.jiseong.homesense.batch.errorhandler.ApiErrorCodeClassifier;
import com.jiseong.homesense.batch.errorhandler.ErrorCodeJudgment;
import com.jiseong.homesense.common.config.DataGoKrProperties;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

import lombok.RequiredArgsConstructor;

/**
 * BAT-CLC-01. 4종 데이터셋(주택유형×거래유형, 아파트 매매는 기본·상세 2개)에 동일한 수집 로직을
 * 파라미터(주택유형·거래유형·dataset ID)만 달리해 적용하는 공통 HTTP 수집기
 * (프로그램목록서 4.5절 통합 설계 원칙 — 개별 구현 4종 대신 단일 파라미터화 수집기).
 */
@Component
@RequiredArgsConstructor
public class RealEstateApiCollector {

    private static final int NUM_OF_ROWS = 1000;

    private final DatasetRegistry datasetRegistry;
    private final ApiErrorCodeClassifier errorCodeClassifier;
    private final DataGoKrProperties dataGoKrProperties;
    private final RestClient restClient;
    private final ApiCallThrottle apiCallThrottle;

    /**
     * 한 조합(housingType×dealCategory)에 걸린 모든 데이터셋(APT+SALE은 기본·상세 2개)의
     * 전체 페이지를 수집해 원본 XML 목록으로 반환한다. 어느 데이터셋이든 resultCode가
     * CONTINUE가 아니면 그 즉시 이 조합의 수집을 중단한다.
     */
    public ApiResponseXml collect(HousingType housingType, DealCategory dealCategory, String sggCd, String dealYmd) {
        List<DatasetPage> pages = new ArrayList<>();
        for (DatasetDescriptor dataset : datasetRegistry.resolve(housingType, dealCategory)) {
            collectAllPages(dataset, sggCd, dealYmd, pages);
        }
        return new ApiResponseXml(pages);
    }

    private void collectAllPages(DatasetDescriptor dataset, String sggCd, String dealYmd, List<DatasetPage> pages) {
        int pageNo = 1;
        while (true) {
            apiCallThrottle.throttle();
            String body = requestPage(dataset, sggCd, dealYmd, pageNo);
            PageMeta meta = OpenApiXmlReader.readMeta(body, dataset.datasetId());

            ErrorCodeJudgment judgment = errorCodeClassifier.checkResultCode(meta.resultCode());
            if (judgment != ErrorCodeJudgment.CONTINUE) {
                throw new OpenApiResultCodeException(dataset.datasetId(), meta.resultCode(), judgment);
            }

            pages.add(new DatasetPage(dataset.datasetId(), body));

            boolean noData = "03".equals(meta.resultCode());
            boolean lastPage = (long) pageNo * NUM_OF_ROWS >= meta.totalCount();
            if (noData || lastPage) {
                return;
            }
            pageNo++;
        }
    }

    private String requestPage(DatasetDescriptor dataset, String sggCd, String dealYmd, int pageNo) {
        // data.go.kr serviceKey는 특수문자(+,/,= 등)를 포함할 수 있어 직접 URL 인코딩한다 —
        // UriComponentsBuilder에 맡기면 이미 인코딩된 값이 다시 인코딩되는 이중 인코딩 위험이 있다.
        String encodedServiceKey = URLEncoder.encode(dataGoKrProperties.serviceKey(), StandardCharsets.UTF_8);
        String uri = dataset.baseUrl()
                + "?serviceKey=" + encodedServiceKey
                + "&LAWD_CD=" + sggCd
                + "&DEAL_YMD=" + dealYmd
                + "&numOfRows=" + NUM_OF_ROWS
                + "&pageNo=" + pageNo;

        // retrieve().body()가 아니라 exchange()를 쓴다 — 서비스키 미등록/만료(resultCode 30/31) 같은
        // 게이트웨이 레벨 오류는 data.go.kr이 200 OK가 아니라 HTTP 4xx로 내려주는데(returnReasonCode
        // 봉투), retrieve()의 기본 동작은 4xx/5xx에서 본문을 읽기도 전에 예외를 던져 그 봉투를
        // OpenApiXmlReader가 아예 볼 수 없다. 그렇다고 모든 오류 상태에서 예외 없이 본문을 통과시키면
        // 이번엔 진짜 일시적 전송 계층 실패(429/5xx, 게이트웨이 봉투가 아닌 일반 오류 페이지)까지
        // BatchExecutionOrchestrator의 재시도 큐를 못 타고 구조적 실패로 오분류된다(P1 코드리뷰
        // 지적). exchange()로 응답 본문을 정확히 한 번만 읽어, 그 내용이 게이트웨이 오류 봉투로
        // 판정 가능할 때만 통과시키고 그 외의 오류 상태는 원래 RestClientException을 그대로 던져
        // 재시도 경로를 유지한다.
        return restClient.get()
                .uri(URI.create(uri))
                .exchange((request, response) -> {
                    byte[] bodyBytes = StreamUtils.copyToByteArray(response.getBody());
                    String body = new String(bodyBytes, StandardCharsets.UTF_8);
                    HttpStatusCode status = response.getStatusCode();
                    if (!status.isError() || looksLikeGatewayErrorEnvelope(body)) {
                        return body;
                    }
                    if (status.is4xxClientError()) {
                        throw HttpClientErrorException.create(status, response.getStatusText(),
                                response.getHeaders(), bodyBytes, StandardCharsets.UTF_8);
                    }
                    throw HttpServerErrorException.create(status, response.getStatusText(),
                            response.getHeaders(), bodyBytes, StandardCharsets.UTF_8);
                });
    }

    /**
     * data.go.kr 게이트웨이 오류 봉투(OpenAPI_ServiceResponse/cmmMsgHeader/returnReasonCode) 또는
     * 정상 응답 봉투(response/header/resultCode)로 판정 가능한 본문인지 가볍게 확인한다 — 엄격한
     * XML 파싱은 OpenApiXmlReader의 책임이라 여기서는 태그 존재 여부만 본다. 이 검사를 통과하지
     * 못하면(진짜 일시적 전송 계층 오류) 예외를 그대로 던진다.
     */
    private static boolean looksLikeGatewayErrorEnvelope(String body) {
        return body != null && (body.contains("resultCode") || body.contains("returnReasonCode"));
    }
}
