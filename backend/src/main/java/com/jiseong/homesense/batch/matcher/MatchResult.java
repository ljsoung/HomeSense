package com.jiseong.homesense.batch.matcher;

import java.math.BigDecimal;

import com.jiseong.homesense.trade.entity.MatchMethod;

/**
 * ComplexMasterMatcher의 매칭 결과. 매칭 실패 시 세 필드 모두 null이며, 이 경우에도
 * building_name(원본 단지명)은 TradeDraft 쪽에 그대로 남아 있어 데이터 유실은 없다.
 */
public record MatchResult(Long complexId, MatchMethod matchMethod, BigDecimal matchConfidence) {

    public static MatchResult unmatched() {
        return new MatchResult(null, null, null);
    }

    public static MatchResult exact(Long complexId, BigDecimal matchConfidence) {
        return new MatchResult(complexId, MatchMethod.EXACT, matchConfidence);
    }

    public static MatchResult similar(Long complexId, BigDecimal matchConfidence) {
        return new MatchResult(complexId, MatchMethod.SIMILAR, matchConfidence);
    }
}
