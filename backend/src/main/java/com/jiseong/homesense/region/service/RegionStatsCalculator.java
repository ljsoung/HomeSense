package com.jiseong.homesense.region.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.region.dto.RegionStats;
import com.jiseong.homesense.trade.repository.TradeRepository;

import lombok.RequiredArgsConstructor;

/**
 * SVC-RGN-01.getInterestSummary() / SVC-FAV-01.getFavoriteRegions() 공용 집계 로직 — 법정동코드
 * 하나에 대해 최근 1개월 평균가·전월 대비 변동률·3.3㎡당 평균가·신규거래 건수를 계산한다. v1.0 설계는
 * 단독·다가구 도메인(SVC-DTH-01)과의 공유를 권장했으나 그 도메인이 MVP 범위 밖이라(CLAUDE.md
 * 최우선 규칙) 지금은 RGN/FAV 두 도메인 전용으로 둔다 — 향후 확장 시 8장 절차대로 재검토한다.
 *
 * <p>pricePerPyeong/newTradeCount는 SVC-FAV-01(MY-02)이 요구해 추가됐다({@link RegionStats} 참고) —
 * changeRate는 기존과 동일하게 avgPrice(원본 dealAmount 평균) 기준이며, 이 두 필드 추가로 계산
 * 기준이 바뀌지는 않는다. 둘 다 "최근 1개월(현재)" 창에서만 계산한다 — 전월 대비 비교가 필요한 값이
 * 아니기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class RegionStatsCalculator {

    private static final int WINDOW_MONTHS = 1;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TradeRepository tradeRepository;

    public RegionStats calculate(String legalDongCd) {
        LocalDate now = LocalDate.now(KST);
        LocalDate to = now.plusDays(1);
        LocalDate currentFrom = now.minusMonths(WINDOW_MONTHS);
        LocalDate previousFrom = now.minusMonths((long) WINDOW_MONTHS * 2);

        BigDecimal currentAvg = averageSaleAmount(legalDongCd, currentFrom, to);
        BigDecimal previousAvg = averageSaleAmount(legalDongCd, previousFrom, currentFrom);
        BigDecimal pricePerPyeong = averagePricePerPyeong(legalDongCd, currentFrom, to);
        long newTradeCount = tradeRepository.countSaleTrades(legalDongCd, currentFrom, to);

        return new RegionStats(currentAvg, changeRate(currentAvg, previousAvg), pricePerPyeong, newTradeCount);
    }

    /**
     * SVC-FAV-01.getFavoriteRegions() 배치 버전 — calculate()를 관심 지역 개수만큼 반복 호출하면
     * 지역당 4개 쿼리(평균가 2회+평당가+건수)가 그대로 곱해져 N+1이 된다(Codex 코드리뷰 P2 지적).
     * legalDongCds 전체를 GROUP BY 쿼리 4개로만 집계해 지역 수와 무관하게 고정된 쿼리 수를 유지한다.
     * 반환 Map은 legalDongCds의 모든 코드를 키로 포함한다(거래가 없으면 RegionStats 필드가 null/0).
     */
    public Map<String, RegionStats> calculateBatch(List<String> legalDongCds) {
        if (legalDongCds.isEmpty()) {
            return Map.of();
        }

        LocalDate now = LocalDate.now(KST);
        LocalDate to = now.plusDays(1);
        LocalDate currentFrom = now.minusMonths(WINDOW_MONTHS);
        LocalDate previousFrom = now.minusMonths((long) WINDOW_MONTHS * 2);

        Map<String, BigDecimal> currentAvgMap = toAvgMap(
                tradeRepository.findAverageSaleAmountGroupedByLegalDongCd(legalDongCds, currentFrom, to));
        Map<String, BigDecimal> previousAvgMap = toAvgMap(
                tradeRepository.findAverageSaleAmountGroupedByLegalDongCd(legalDongCds, previousFrom, currentFrom));
        Map<String, BigDecimal> pricePerPyeongMap = toAvgMap(
                tradeRepository.findAveragePricePerPyeongGroupedByLegalDongCd(legalDongCds, currentFrom, to));
        Map<String, Long> newTradeCountMap = tradeRepository
                .countSaleTradesGroupedByLegalDongCd(legalDongCds, currentFrom, to).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));

        return legalDongCds.stream().distinct().collect(Collectors.toMap(cd -> cd, cd -> {
            BigDecimal currentAvg = currentAvgMap.get(cd);
            BigDecimal previousAvg = previousAvgMap.get(cd);
            return new RegionStats(currentAvg, changeRate(currentAvg, previousAvg),
                    pricePerPyeongMap.get(cd), newTradeCountMap.getOrDefault(cd, 0L));
        }));
    }

    private Map<String, BigDecimal> toAvgMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(
                row -> (String) row[0],
                row -> BigDecimal.valueOf((Double) row[1]).setScale(0, RoundingMode.HALF_UP)));
    }

    private BigDecimal averageSaleAmount(String legalDongCd, LocalDate from, LocalDate to) {
        return tradeRepository.findAverageSaleAmount(legalDongCd, from, to)
                .map(avg -> BigDecimal.valueOf(avg).setScale(0, RoundingMode.HALF_UP))
                .orElse(null);
    }

    private BigDecimal averagePricePerPyeong(String legalDongCd, LocalDate from, LocalDate to) {
        return tradeRepository.findAveragePricePerPyeongForSale(legalDongCd, from, to)
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
