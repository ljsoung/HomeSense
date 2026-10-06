package com.jiseong.homesense.batch.notifier;

import java.math.BigDecimal;

/**
 * 평가 대상 알림 설정 한 건(notification_setting ⨝ 관심 매물/지역 ⨝ ACTIVE 회원).
 * 관심 매물이면 complexId, 관심 지역이면 legalDongCd(등록된 코드 그대로)가 채워진다.
 */
record WatchTarget(
        long settingId,
        long userId,
        Long complexId,
        String legalDongCd,
        String targetName,
        BigDecimal thresholdPct,
        boolean newTradeAlert) {

    boolean isRegion() {
        return complexId == null;
    }
}
