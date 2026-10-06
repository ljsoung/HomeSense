package com.jiseong.homesense.batch.notifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * BAT-NTF-01 평가 입력(설계서 4.9 시그니처 {@code evaluateAfterLoad(Set, Set)}를 확장 — D2).
 *
 * @param runStartedAt 이번 BAT-SCH-01 런 시작 시각(초 단위 절삭). {@code trade.created_at >= runStartedAt}이
 *                     "이번 런에 신규 INSERT된 거래"다. trade.created_at과 같은 시간 소스(JVM 기본 타임존의
 *                     {@code LocalDateTime.now()}, TradeChunkLoader)로 잡아야 한다 — KST Clock으로 잡으면 UTC JVM에서
 *                     9시간 어긋나 비교가 깨진다.
 * @param runDate      기준 평균 기간 계산용 런 날짜(KST).
 * @param complexIds   이번 런에 적재(INSERT/UPDATE)된 거래가 참조한 단지 — 관심 매물 설정 조회 범위.
 * @param legalDongCds 이번 런에 적재된 거래가 참조한 법정동 — 관심 지역 설정과 신규 거래 조회 범위.
 */
public record NotificationTriggerContext(
        LocalDateTime runStartedAt,
        LocalDate runDate,
        Set<Long> complexIds,
        Set<String> legalDongCds) {

    public NotificationTriggerContext {
        complexIds = Set.copyOf(complexIds);
        legalDongCds = Set.copyOf(legalDongCds);
    }

    boolean hasNoAffectedTargets() {
        return complexIds.isEmpty() && legalDongCds.isEmpty();
    }
}
