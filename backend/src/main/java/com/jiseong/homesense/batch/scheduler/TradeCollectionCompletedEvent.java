package com.jiseong.homesense.batch.scheduler;

import java.time.YearMonth;

/**
 * BatchExecutionOrchestrator가 targetMonth의 전체 조합 순회를 끝마치면 발행한다.
 * BAT-NTF-01(관심대상 조건평가, 4단계 개인화)이 이 이벤트를 구독해 알림 평가를 트리거할 예정이다 —
 * 4단계 이전에는 구독자가 없어 조용히 소비되지 않을 뿐, 발행 시점 자체는 이미 파이프라인 순서
 * (CLAUDE.md 배치 파이프라인 표)대로 고정해 둔다.
 */
public record TradeCollectionCompletedEvent(YearMonth targetMonth) {
}
