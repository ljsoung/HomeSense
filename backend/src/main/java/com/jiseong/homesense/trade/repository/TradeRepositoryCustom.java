package com.jiseong.homesense.trade.repository;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.jiseong.homesense.trade.dto.DealTypeFilter;
import com.jiseong.homesense.trade.dto.TradeSearchCondition;
import com.jiseong.homesense.trade.dto.TradeSummaryResponse;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;

interface TradeRepositoryCustom {

    Page<TradeSummaryResponse> search(TradeSearchCondition condition, Pageable pageable);

    /** deal_date DESC(동률 시 trade_id DESC로 안정화) — cancel_yn=true 건도 포함해 그대로 반환한다. */
    List<Trade> findHistory(Long complexId, HousingType housingType, DealTypeFilter dealType);

    /**
     * complexIds 각각의 대표 거래(취소되지 않은 거래 중 최신 1건, ComplexRepositoryCustomImpl.search()와
     * 같은 동률 판정: MAX(dealDate) → MAX(tradeId))를 단 한 번의 쿼리로 모아 반환한다. 대표 거래가 없는
     * complex_id는 결과 Map에 키 자체가 없다.
     *
     * <p>SVC-FAV-01.getFavoriteProperties()가 최초로 도입했고, SVC-CPX-01.getPopular()·SVC-RCV-01.getRecent()도
     * 같은 "대표 거래" 개념이 필요해 이 메서드를 그대로 재사용한다(CLAUDE.md "CPX-RCV-RGN price/area/floor
     * 보강" 절 참고) — 세 도메인이 각자 다른 선정 기준을 구현하면 같은 단지가 화면마다 다른 가격/층을
     * 보여주는 정합성 버그가 생기므로, 이 메서드가 유일한 소스여야 한다.
     */
    Map<Long, Trade> findRecentTradesByComplexIds(List<Long> complexIds);
}
