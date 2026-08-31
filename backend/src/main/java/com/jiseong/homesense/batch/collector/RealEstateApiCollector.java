package com.jiseong.homesense.batch.collector;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
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

        return restClient.get()
                .uri(URI.create(uri))
                .retrieve()
                .body(String.class);
    }
}
