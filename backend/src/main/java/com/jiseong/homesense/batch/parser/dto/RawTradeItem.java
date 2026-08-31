package com.jiseong.homesense.batch.parser.dto;

import java.util.Map;

/**
 * TradeXmlParser가 API 응답 XML의 &lt;item&gt; 하나를 태그명 -&gt; 텍스트 값으로 그대로 담은 원시 데이터.
 * 데이터셋(아파트/연립다세대, 매매/전월세)마다 태그 구성이 달라질 수 있어 필드를 미리 고정하지 않는다.
 */
public record RawTradeItem(Map<String, String> fields) {

    public String get(String tagName) {
        return fields.get(tagName);
    }
}
