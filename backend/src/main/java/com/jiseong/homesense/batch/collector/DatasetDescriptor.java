package com.jiseong.homesense.batch.collector;

/**
 * data.go.kr 데이터셋 1개의 식별자와 서비스 URL.
 */
public record DatasetDescriptor(String datasetId, String baseUrl) {
}
