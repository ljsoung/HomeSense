package com.jiseong.homesense.batch.collector;

/**
 * 페이지네이션·에러코드 판정에 필요한 최소 정보. item 파싱은 BAT-PRS-01 책임이라 담지 않는다.
 */
record PageMeta(String resultCode, int totalCount) {
}
