package com.jiseong.homesense.trade.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.RentType;
import com.jiseong.homesense.trade.entity.Trade;

/**
 * DTL-01 거래상세 모달 응답. dedup_hash·match_confidence 등 내부 관리용 컬럼은 제외하고, 계약일·
 * 전용면적·층·거래금액·동(apt_dong)·거래유형(dealing_type)·매도/매수자 구분·등기일자·해제여부
 * (및 해제사유발생일)·토지임대부 여부만 담는다(설계서 원문 그대로).
 *
 * <p>aptDong은 소유권 이전등기 완료 건에만 값이 존재한다 — NULL이면 aptDongPending을 true로 내려
 * 프론트가 "등기 완료 후 제공" 안내로 대체하게 한다.
 */
public record TradeDetailResponse(
        Long tradeId,
        LocalDate dealDate,
        BigDecimal excluUseArea,
        Short floor,
        DealCategory dealCategory,
        RentType rentType,
        Long dealAmount,
        Long depositAmount,
        Long monthlyRentAmount,
        String aptDong,
        boolean aptDongPending,
        String dealingType,
        String sellerType,
        String buyerType,
        LocalDate registrationDate,
        boolean isCancelled,
        LocalDate cancelDate,
        Boolean landLeaseYn) {

    public static TradeDetailResponse from(Trade t) {
        return new TradeDetailResponse(
                t.getTradeId(),
                t.getDealDate(),
                t.getExcluUseArea(),
                t.getFloor(),
                t.getDealCategory(),
                t.getRentType(),
                t.getDealAmount(),
                t.getDepositAmount(),
                t.getMonthlyRentAmount(),
                t.getAptDong(),
                t.getAptDong() == null,
                t.getDealingType(),
                t.getSellerType(),
                t.getBuyerType(),
                t.getRegistrationDate(),
                t.isCancelYn(),
                t.getCancelDate(),
                t.getLandLeaseYn());
    }
}
