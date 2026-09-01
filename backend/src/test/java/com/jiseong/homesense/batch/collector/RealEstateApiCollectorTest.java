package com.jiseong.homesense.batch.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.jiseong.homesense.batch.errorhandler.ApiErrorCodeClassifier;
import com.jiseong.homesense.batch.errorhandler.ErrorCodeJudgment;
import com.jiseong.homesense.common.config.DataGoKrProperties;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

class RealEstateApiCollectorTest {

    private static final String SERVICE_KEY = "abc+DEF/123==";
    private static final String ENCODED_SERVICE_KEY = "abc%2BDEF%2F123%3D%3D";

    private final DatasetRegistry datasetRegistry = new DatasetRegistry();
    private final ApiErrorCodeClassifier errorCodeClassifier = new ApiErrorCodeClassifier();
    private final DataGoKrProperties dataGoKrProperties = new DataGoKrProperties(SERVICE_KEY);

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;

    private RealEstateApiCollector newCollector() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();
        return new RealEstateApiCollector(
                datasetRegistry, errorCodeClassifier, dataGoKrProperties, restClient, new ApiCallThrottle());
    }

    private static String responseXml(String resultCode, int totalCount, int itemCount) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < itemCount; i++) {
            items.append("<item><sggCd>11680</sggCd></item>");
        }
        return """
                <response>
                    <header>
                        <resultCode>%s</resultCode>
                        <resultMsg>OK</resultMsg>
                    </header>
                    <body>
                        <items>%s</items>
                        <numOfRows>1000</numOfRows>
                        <pageNo>1</pageNo>
                        <totalCount>%d</totalCount>
                    </body>
                </response>
                """.formatted(resultCode, items, totalCount);
    }

    @Test
    void 데이터셋이_1개인_조합은_요청_1건으로_끝난다() {
        RealEstateApiCollector collector = newCollector();
        String expectedUri = "https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent"
                + "?serviceKey=" + ENCODED_SERVICE_KEY
                + "&LAWD_CD=11680&DEAL_YMD=202401&numOfRows=1000&pageNo=1";
        mockServer.expect(requestTo(expectedUri))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseXml("000", 1, 1), MediaType.APPLICATION_XML));

        ApiResponseXml result = collector.collect(HousingType.APT, DealCategory.RENT, "11680", "202401");

        assertThat(result.pages()).hasSize(1);
        assertThat(result.pages().get(0).datasetId()).isEqualTo("15126474");
        mockServer.verify();
    }

    @Test
    void 아파트_매매는_기본과_상세_두_URL_모두에_요청한다() {
        RealEstateApiCollector collector = newCollector();
        mockServer.expect(requestTo("https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade"
                        + "?serviceKey=" + ENCODED_SERVICE_KEY
                        + "&LAWD_CD=11680&DEAL_YMD=202401&numOfRows=1000&pageNo=1"))
                .andRespond(withSuccess(responseXml("000", 1, 1), MediaType.APPLICATION_XML));
        mockServer.expect(requestTo("https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev"
                        + "?serviceKey=" + ENCODED_SERVICE_KEY
                        + "&LAWD_CD=11680&DEAL_YMD=202401&numOfRows=1000&pageNo=1"))
                .andRespond(withSuccess(responseXml("000", 1, 1), MediaType.APPLICATION_XML));

        ApiResponseXml result = collector.collect(HousingType.APT, DealCategory.SALE, "11680", "202401");

        // 회귀 테스트: 페이지를 그냥 이어붙이기만 하면 기본(15126469)·상세(15126468) 중 어느 페이지가
        // 어느 데이터셋 것인지 알 수 없어 TradeFieldMapper에 잘못된 datasetId가 넘어갈 수 있었다.
        assertThat(result.pages()).extracting(DatasetPage::datasetId)
                .containsExactly("15126469", "15126468");
        mockServer.verify();
    }

    @Test
    void 같은_조합_안의_여러_요청도_최소_지연을_두고_나간다() {
        // 회귀 테스트: 스로틀이 BatchExecutionOrchestrator의 조합 경계에만 있으면, 한 조합이
        // 데이터셋 2개(APT+SALE)로 갈라지는 이 케이스처럼 collect() 내부에서 연달아 나가는 요청
        // 사이는 무방비 상태가 된다 — ApiCallThrottle이 requestPage() 직전에서 간격을 강제해야 한다.
        RealEstateApiCollector collector = newCollector();
        mockServer.expect(requestTo("https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade"
                        + "?serviceKey=" + ENCODED_SERVICE_KEY
                        + "&LAWD_CD=11680&DEAL_YMD=202401&numOfRows=1000&pageNo=1"))
                .andRespond(withSuccess(responseXml("000", 1, 1), MediaType.APPLICATION_XML));
        mockServer.expect(requestTo("https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev"
                        + "?serviceKey=" + ENCODED_SERVICE_KEY
                        + "&LAWD_CD=11680&DEAL_YMD=202401&numOfRows=1000&pageNo=1"))
                .andRespond(withSuccess(responseXml("000", 1, 1), MediaType.APPLICATION_XML));

        long start = System.currentTimeMillis();
        collector.collect(HousingType.APT, DealCategory.SALE, "11680", "202401");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThanOrEqualTo(40);
        mockServer.verify();
    }

    @Test
    void totalCount가_numOfRows보다_많으면_다음_페이지를_이어서_요청한다() {
        RealEstateApiCollector collector = newCollector();
        mockServer.expect(requestTo("https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent"
                        + "?serviceKey=" + ENCODED_SERVICE_KEY
                        + "&LAWD_CD=11680&DEAL_YMD=202401&numOfRows=1000&pageNo=1"))
                .andRespond(withSuccess(responseXml("000", 1500, 1000), MediaType.APPLICATION_XML));
        mockServer.expect(requestTo("https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent"
                        + "?serviceKey=" + ENCODED_SERVICE_KEY
                        + "&LAWD_CD=11680&DEAL_YMD=202401&numOfRows=1000&pageNo=2"))
                .andRespond(withSuccess(responseXml("000", 1500, 500), MediaType.APPLICATION_XML));

        ApiResponseXml result = collector.collect(HousingType.APT, DealCategory.RENT, "11680", "202401");

        assertThat(result.pages()).hasSize(2);
        assertThat(result.pages()).extracting(DatasetPage::datasetId)
                .containsExactly("15126474", "15126474");
        mockServer.verify();
    }

    @Test
    void resultCode가_03이면_한_페이지만_요청하고_빈_결과로_끝낸다() {
        RealEstateApiCollector collector = newCollector();
        mockServer.expect(requestTo("https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent"
                        + "?serviceKey=" + ENCODED_SERVICE_KEY
                        + "&LAWD_CD=11680&DEAL_YMD=202401&numOfRows=1000&pageNo=1"))
                .andRespond(withSuccess(responseXml("03", 0, 0), MediaType.APPLICATION_XML));

        ApiResponseXml result = collector.collect(HousingType.APT, DealCategory.RENT, "11680", "202401");

        assertThat(result.pages()).hasSize(1);
        mockServer.verify();
    }

    @Test
    void resultCode가_CONTINUE가_아니면_이_조합의_수집을_즉시_중단한다() {
        RealEstateApiCollector collector = newCollector();
        mockServer.expect(requestTo("https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent"
                        + "?serviceKey=" + ENCODED_SERVICE_KEY
                        + "&LAWD_CD=11680&DEAL_YMD=202401&numOfRows=1000&pageNo=1"))
                .andRespond(withSuccess(responseXml("30", 0, 0), MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> collector.collect(HousingType.APT, DealCategory.RENT, "11680", "202401"))
                .isInstanceOf(OpenApiResultCodeException.class)
                .extracting(e -> ((OpenApiResultCodeException) e).resultCode())
                .isEqualTo("30");
        mockServer.verify();
    }

    @Test
    void 게이트웨이_오류_봉투의_returnReasonCode도_ABORT_BATCH로_판정한다() {
        // 회귀 테스트: 서비스키 미등록·만료 같은 게이트웨이 오류는 header/resultCode가 아니라
        // OpenAPI_ServiceResponse/cmmMsgHeader/returnReasonCode로 내려온다. 이 봉투를 못 읽으면
        // judgment 없는 일반 OpenApiResponseException으로 떨어져 호출부가 ABORT_BATCH를 알 수 없다.
        String gatewayErrorXml = """
                <OpenAPI_ServiceResponse>
                    <cmmMsgHeader>
                        <errMsg>SERVICE ERROR</errMsg>
                        <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                        <returnReasonCode>30</returnReasonCode>
                    </cmmMsgHeader>
                </OpenAPI_ServiceResponse>
                """;
        RealEstateApiCollector collector = newCollector();
        mockServer.expect(requestTo("https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent"
                        + "?serviceKey=" + ENCODED_SERVICE_KEY
                        + "&LAWD_CD=11680&DEAL_YMD=202401&numOfRows=1000&pageNo=1"))
                .andRespond(withSuccess(gatewayErrorXml, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> collector.collect(HousingType.APT, DealCategory.RENT, "11680", "202401"))
                .asInstanceOf(type(OpenApiResultCodeException.class))
                .satisfies(e -> {
                    assertThat(e.resultCode()).isEqualTo("30");
                    assertThat(e.judgment()).isEqualTo(ErrorCodeJudgment.ABORT_BATCH);
                });
        mockServer.verify();
    }

    @Test
    void resultCode가_000인데_totalCount가_없으면_0으로_넘기지_않고_예외를_던진다() {
        // 회귀 테스트: totalCount를 조용히 0으로 취급하면 pageNo(1)*1000 >= 0이 참이 되어
        // 실제로는 여러 페이지가 남아 있는데도 첫 페이지에서 수집이 조용히 끊길 수 있다.
        String missingTotalCountXml = """
                <response>
                    <header>
                        <resultCode>000</resultCode>
                        <resultMsg>OK</resultMsg>
                    </header>
                    <body>
                        <items><item><sggCd>11680</sggCd></item></items>
                        <numOfRows>1000</numOfRows>
                        <pageNo>1</pageNo>
                    </body>
                </response>
                """;
        RealEstateApiCollector collector = newCollector();
        mockServer.expect(requestTo("https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent"
                        + "?serviceKey=" + ENCODED_SERVICE_KEY
                        + "&LAWD_CD=11680&DEAL_YMD=202401&numOfRows=1000&pageNo=1"))
                .andRespond(withSuccess(missingTotalCountXml, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> collector.collect(HousingType.APT, DealCategory.RENT, "11680", "202401"))
                .isInstanceOf(OpenApiResponseException.class);
        mockServer.verify();
    }

    @Test
    void resultCode가_000인데_totalCount가_숫자가_아니면_예외를_던진다() {
        RealEstateApiCollector collector = newCollector();
        mockServer.expect(requestTo("https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent"
                        + "?serviceKey=" + ENCODED_SERVICE_KEY
                        + "&LAWD_CD=11680&DEAL_YMD=202401&numOfRows=1000&pageNo=1"))
                .andRespond(withSuccess("""
                        <response>
                            <header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>
                            <body>
                                <items><item><sggCd>11680</sggCd></item></items>
                                <totalCount>N/A</totalCount>
                            </body>
                        </response>
                        """, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> collector.collect(HousingType.APT, DealCategory.RENT, "11680", "202401"))
                .isInstanceOf(OpenApiResponseException.class);
        mockServer.verify();
    }
}
