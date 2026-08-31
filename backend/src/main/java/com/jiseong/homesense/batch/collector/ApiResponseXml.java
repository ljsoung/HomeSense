package com.jiseong.homesense.batch.collector;

import java.util.List;

/**
 * RealEstateApiCollector.collect()가 한 조합(housingType×dealCategory)에 대해 수집한
 * 모든 데이터셋·모든 페이지의 원본 응답 XML. 각 원소는 그 자체로 완전한 API 응답 문서라
 * BAT-PRS-01(TradeXmlParser.parse)에 하나씩 그대로 넘기면 된다.
 */
public record ApiResponseXml(List<String> pageBodies) {
}
