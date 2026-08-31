package com.jiseong.homesense.batch.collector;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

/**
 * BAT-CLC-01. 프로그램목록서 4.5.1절 데이터셋 매핑표를 설정 테이블로 관리한다.
 * 아파트 매매(APT+SALE)만 기본·상세 2개 데이터셋으로 갈라지고 나머지 조합은 1개씩이다 —
 * 그래서 resolve()는 항상 List를 반환하고, 기본/상세를 몰라도 되는 호출부는 그 리스트를
 * 그대로 순회하며 collect()에 넘기면 된다.
 *
 * <p>5개 URL 전부 data.go.kr 공식 페이지 및 실제 호출 사례로 대조 검증 완료
 * (오퍼레이션 경로까지 포함한 완전한 호출 URL — {서비스명}/get{서비스명} 규칙).
 */
@Component
public class DatasetRegistry {

    private record DatasetKey(HousingType housingType, DealCategory dealCategory) {
    }

    private static final String BASE_HOST = "https://apis.data.go.kr/1613000/";

    private static final Map<DatasetKey, List<DatasetDescriptor>> DATASETS = Map.of(
            new DatasetKey(HousingType.APT, DealCategory.SALE), List.of(
                    new DatasetDescriptor("15126469", BASE_HOST + "RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade"),
                    new DatasetDescriptor("15126468", BASE_HOST + "RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev")),
            new DatasetKey(HousingType.APT, DealCategory.RENT), List.of(
                    new DatasetDescriptor("15126474", BASE_HOST + "RTMSDataSvcAptRent/getRTMSDataSvcAptRent")),
            new DatasetKey(HousingType.VILLA, DealCategory.SALE), List.of(
                    new DatasetDescriptor("15126467", BASE_HOST + "RTMSDataSvcRHTrade/getRTMSDataSvcRHTrade")),
            new DatasetKey(HousingType.VILLA, DealCategory.RENT), List.of(
                    new DatasetDescriptor("15126473", BASE_HOST + "RTMSDataSvcRHRent/getRTMSDataSvcRHRent")));

    public List<DatasetDescriptor> resolve(HousingType housingType, DealCategory dealCategory) {
        List<DatasetDescriptor> datasets = DATASETS.get(new DatasetKey(housingType, dealCategory));
        if (datasets == null) {
            throw new IllegalArgumentException("등록되지 않은 조합이다: " + housingType + "/" + dealCategory);
        }
        return datasets;
    }
}