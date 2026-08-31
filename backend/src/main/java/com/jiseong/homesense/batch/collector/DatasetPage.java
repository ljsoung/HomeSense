package com.jiseong.homesense.batch.collector;

/**
 * 원본 응답 XML 한 페이지와, 그 페이지가 어느 dataset_id에서 나왔는지를 함께 담는다.
 * APT+SALE처럼 한 조합이 기본·상세 2개 데이터셋으로 갈라지면 페이지를 그냥 이어붙인 리스트만으로는
 * 어느 페이지가 어느 데이터셋 것인지 알 수 없다 — TradeFieldMapper.mapToUnifiedModel이 item마다
 * datasetId를 요구하므로, 이 매핑을 페이지 단위로 명시적으로 들고 있어야 한다.
 */
public record DatasetPage(String datasetId, String body) {
}
