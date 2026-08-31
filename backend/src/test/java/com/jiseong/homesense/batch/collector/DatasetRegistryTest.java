package com.jiseong.homesense.batch.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

class DatasetRegistryTest {

    private final DatasetRegistry registry = new DatasetRegistry();

    @Test
    void 아파트_매매는_기본과_상세_2개_데이터셋을_모두_반환한다() {
        List<DatasetDescriptor> datasets = registry.resolve(HousingType.APT, DealCategory.SALE);

        assertThat(datasets).containsExactly(
                new DatasetDescriptor("15126469", "https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade"),
                new DatasetDescriptor("15126468", "https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev"));
    }

    @Test
    void 아파트_전월세는_데이터셋_1개를_반환한다() {
        List<DatasetDescriptor> datasets = registry.resolve(HousingType.APT, DealCategory.RENT);

        assertThat(datasets).containsExactly(
                new DatasetDescriptor("15126474", "https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent"));
    }

    @Test
    void 연립다세대_매매는_데이터셋_1개를_반환한다() {
        List<DatasetDescriptor> datasets = registry.resolve(HousingType.VILLA, DealCategory.SALE);

        assertThat(datasets).containsExactly(
                new DatasetDescriptor("15126467", "https://apis.data.go.kr/1613000/RTMSDataSvcRHTrade/getRTMSDataSvcRHTrade"));
    }

    @Test
    void 연립다세대_전월세는_데이터셋_1개를_반환한다() {
        List<DatasetDescriptor> datasets = registry.resolve(HousingType.VILLA, DealCategory.RENT);

        assertThat(datasets).containsExactly(
                new DatasetDescriptor("15126473", "https://apis.data.go.kr/1613000/RTMSDataSvcRHRent/getRTMSDataSvcRHRent"));
    }
}
