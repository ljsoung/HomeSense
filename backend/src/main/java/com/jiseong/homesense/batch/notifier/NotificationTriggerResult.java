package com.jiseong.homesense.batch.notifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * BAT-NTF-01 실행 결과 집계(D8). batch_log는 API 조합 단위 스키마라 쓰지 않고 구조화 로그로만 남긴다.
 *
 * @param evaluatedSettings  평가한 알림 설정 수(이번 런과 연관된 ACTIVE 회원의 설정)
 * @param newTradeCreated    생성한 NEW_TRADE 알림 수
 * @param priceChangeCreated 생성한 PRICE_CHANGE 알림 수
 * @param skipped            스킵 사유별 건수(설정 × 알림 유형 단위)
 * @param failed             예외로 처리하지 못한 설정 수
 */
public record NotificationTriggerResult(
        int evaluatedSettings,
        int newTradeCreated,
        int priceChangeCreated,
        Map<SkipReason, Integer> skipped,
        int failed) {

    public NotificationTriggerResult {
        skipped = Map.copyOf(skipped);
    }

    static NotificationTriggerResult empty() {
        return new NotificationTriggerResult(0, 0, 0, Map.of(), 0);
    }

    public int skippedCount(SkipReason reason) {
        return skipped.getOrDefault(reason, 0);
    }

    public enum SkipReason {
        /** 신규거래 알림을 끈 설정. */
        NEW_TRADE_ALERT_OFF,
        /** 이번 런에 대상의 신규 거래가 없음. */
        NO_NEW_TRADE,
        /** 이번 런에 대상의 신규 매매 거래(가격 있음)가 없음. */
        NO_NEW_SALE,
        /** 기준 평균의 거래 수가 최소 표본보다 적음. */
        BASELINE_TOO_SMALL,
        /** 변동률이 임계치 미만이거나 0.0. */
        BELOW_THRESHOLD,
        /** 관심 지역 코드가 활성 법정동이 아니라 집계 범위를 정할 수 없음. */
        REGION_UNRESOLVED
    }

    /** 평가 중 누적용. */
    static final class Tally {
        int evaluated;
        int newTrade;
        int priceChange;
        int failed;
        final Map<SkipReason, Integer> skipped = new EnumMap<>(SkipReason.class);

        void skip(SkipReason reason) {
            skipped.merge(reason, 1, Integer::sum);
        }

        NotificationTriggerResult toResult() {
            return new NotificationTriggerResult(evaluated, newTrade, priceChange, skipped, failed);
        }
    }
}
