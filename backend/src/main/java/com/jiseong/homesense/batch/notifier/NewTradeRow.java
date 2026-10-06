package com.jiseong.homesense.batch.notifier;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.RentType;

/**
 * 이번 런에 신규 적재된(created_at >= 런 시작 시각) 해제되지 않은 거래 한 건. 금액 단위는 만원.
 * NEW_TRADE 집계·대표 거래·가격변동의 신규 평균을 모두 이 행들로 계산한다.
 */
record NewTradeRow(
        long tradeId,
        Long complexId,
        String legalDongCd,
        LocalDate dealDate,
        DealCategory dealCategory,
        RentType rentType,
        Long dealAmount,
        Long depositAmount,
        Long monthlyRentAmount,
        BigDecimal excluUseArea,
        Short floor) {

    /** 가격변동 신규 평균에 들어가는 매매 거래인지 — 기준 평균 쿼리와 같은 조건(D4). */
    boolean isPricedSale() {
        return dealCategory == DealCategory.SALE
                && dealAmount != null
                && excluUseArea != null
                && excluUseArea.signum() > 0;
    }

    /** 최신 계약일, 같으면 큰 trade_id가 대표(D3). */
    boolean isMoreRecentThan(NewTradeRow other) {
        int byDate = dealDate.compareTo(other.dealDate);
        return byDate != 0 ? byDate > 0 : tradeId > other.tradeId;
    }
}
