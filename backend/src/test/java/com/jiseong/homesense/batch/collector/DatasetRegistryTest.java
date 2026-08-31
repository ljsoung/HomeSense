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

        assertThat(datasets).extracting(DatasetDescriptor::datasetId)
                .containsExactly("15126469", "15126468");
    }

    @Test
    void 아파트_전월세는_데이터셋_1개를_반환한다() {
        List<DatasetDescriptor> datasets = registry.resolve(HousingType.APT, DealCategory.RENT);

        assertThat(datasets).extracting(DatasetDescriptor::datasetId).containsExactly("15126474");
    }

    @Test
    void 연립다세대_매매는_데이터셋_1개를_반환한다() {
        List<DatasetDescriptor> datasets = registry.resolve(HousingType.VILLA, DealCategory.SALE);

        assertThat(datasets).extracting(DatasetDescriptor::datasetId).containsExactly("15126467");
    }

    @Test
    void 연립다세대_전월세는_데이터셋_1개를_반환한다() {
        List<DatasetDescriptor> datasets = registry.resolve(HousingType.VILLA, DealCategory.RENT);

        assertThat(datasets).extracting(DatasetDescriptor::datasetId).containsExactly("15126473");
    }
}
