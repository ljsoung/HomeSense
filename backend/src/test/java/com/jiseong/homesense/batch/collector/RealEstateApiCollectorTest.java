package com.jiseong.homesense.batch.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.jiseong.homesense.batch.errorhandler.ApiErrorCodeClassifier;
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
        return new RealEstateApiCollector(datasetRegistry, errorCodeClassifier, dataGoKrProperties, restClient);
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

        assertThat(result.pageBodies()).hasSize(1);
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

        assertThat(result.pageBodies()).hasSize(2);
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

        assertThat(result.pageBodies()).hasSize(2);
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

        assertThat(result.pageBodies()).hasSize(1);
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
}
