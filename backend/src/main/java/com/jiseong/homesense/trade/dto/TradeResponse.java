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
 *
 * <p>cancelDate는 해제사유발생일(cancel_date, 원천 cdealDay)이다 — UI정의서 v2.1.1 5.3절 예외 처리표가 이력
 * 테이블 행에 "해제" 라벨과 해제사유발생일을 함께 요구한다. 해제되지 않았거나 전월세 거래(원천 데이터에 해제
 * 정보가 없다)는 null이다. dealingType은 거래유형(dealing_type, 원천 dealingGbn)으로 매매 이력 테이블의 거래유형 열에
 * 쓴다. 값은 BAT-PRS-01(TradeFieldMapper)이 정규화한 코드 {@code AGENT}(중개거래)/{@code DIRECT}(직거래)다. 전월세 원천 데이터에는 없어 null이다. 둘 다 non_null 직렬화라 null이면 JSON에서 키가
 * 빠진다. 이력 조회는 캐시를 적용하지 않아 필드 추가에 따른 캐시 버전업이 필요 없다.
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
        String dealingType,
        boolean isCancelled,
        LocalDate cancelDate,
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
                t.getDealingType(),
                t.isCancelYn(),
                t.getCancelDate(),
                t.getRegistrationDate() != null);
    }
}
