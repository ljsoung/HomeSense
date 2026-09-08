package com.jiseong.homesense.trade.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/** SVC-TRD-01.getDetail() — 존재하지 않는 거래 ID로 조회한 경우. */
public class TradeNotFoundException extends BusinessException {

    public TradeNotFoundException() {
        super("TRADE_NOT_FOUND", "존재하지 않는 거래입니다", HttpStatus.NOT_FOUND);
    }
}
