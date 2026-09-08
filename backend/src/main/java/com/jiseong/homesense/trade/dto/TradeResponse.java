package com.jiseong.homesense.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.RentType;
import com.jiseong.homesense.trade.entity.Trade;

/**
 * DTL-01 단지별 실거래 이력 목록 항목. complexId는 요청 파라미터로 이미 고정돼 있어 항목에 다시
 * 담지 않는다.
 *
 * <p>isCancelled는 cancel_yn을 그대로 노출한다 — 해제된 거래도 결과에서 빼지 않고 이 플래그로
 * 프론트가 취소선 처리하게 한다(DTL-01 예외 처리 "해제된 거래" 대응). isRegistered는
 * registration_date가 NULL이 아니면 true다("등기 전" 라벨 대응).
 */
public record TradeResponse(
        Long tradeId,
        LocalDate dealDate,
        DealCategory dealCategory,
        RentType rentType,
        BigDecimal excluUseArea,
        Short floor,
        Long dealAmount,
        Long depositAmount,
        Long monthlyRentAmount,
        boolean isCancelled,
        boolean isRegistered) {

    public static TradeResponse of(Trade t) {
        return new TradeResponse(
                t.getTradeId(),
                t.getDealDate(),
                t.getDealCategory(),
                t.getRentType(),
                t.getExcluUseArea(),
                t.getFloor(),
                t.getDealAmount(),
                t.getDepositAmount(),
                t.getMonthlyRentAmount(),
                t.isCancelYn(),
                t.getRegistrationDate() != null);
    }
}
