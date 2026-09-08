package com.jiseong.homesense.region.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.region.dto.RegionStats;
import com.jiseong.homesense.trade.repository.TradeRepository;

import lombok.RequiredArgsConstructor;

/**
 * SVC-RGN-01.getInterestSummary() 전용 집계 로직 — 법정동코드 하나에 대해 최근 1개월 평균가와
 * 전월 대비 변동률을 계산한다. v1.0 설계는 단독·다가구 도메인(SVC-DTH-01)과의 공유를 권장했으나
 * 그 도메인이 MVP 범위 밖이라(CLAUDE.md 최우선 규칙) 지금은 RGN 전용으로 둔다 — 향후 확장 시
 * 8장 절차대로 재검토한다.
 */
@Component
@RequiredArgsConstructor
public class RegionStatsCalculator {

    private static final int WINDOW_MONTHS = 1;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TradeRepository tradeRepository;

    public RegionStats calculate(String legalDongCd) {
        LocalDate now = LocalDate.now(KST);
        LocalDate currentFrom = now.minusMonths(WINDOW_MONTHS);
        LocalDate previousFrom = now.minusMonths((long) WINDOW_MONTHS * 2);

        BigDecimal currentAvg = averageSaleAmount(legalDongCd, currentFrom, now);
        BigDecimal previousAvg = averageSaleAmount(legalDongCd, previousFrom, currentFrom);

        return new RegionStats(currentAvg, changeRate(currentAvg, previousAvg));
    }

    private BigDecimal averageSaleAmount(String legalDongCd, LocalDate from, LocalDate to) {
        return tradeRepository.findAverageSaleAmount(legalDongCd, from, to)
                .map(avg -> BigDecimal.valueOf(avg).setScale(0, RoundingMode.HALF_UP))
                .orElse(null);
    }

    private BigDecimal changeRate(BigDecimal currentAvg, BigDecimal previousAvg) {
        if (currentAvg == null || previousAvg == null || previousAvg.signum() == 0) {
            return null;
        }
        return currentAvg.subtract(previousAvg)
                .divide(previousAvg, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
