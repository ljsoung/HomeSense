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
     * SVC-FAV-01.getFavoriteProperties() 배치 버전 — complexIds 각각의 대표 거래(취소되지 않은 거래
     * 중 최신 1건, ComplexRepositoryCustomImpl.search()와 같은 동률 판정: MAX(dealDate) →
     * MAX(tradeId))를 단 한 번의 쿼리로 모아 반환한다. 대표 거래가 없는 complex_id는 결과 Map에
     * 키 자체가 없다.
     */
    Map<Long, Trade> findRecentTradesByComplexIds(List<Long> complexIds);
}
