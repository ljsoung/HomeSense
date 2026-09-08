package com.jiseong.homesense.trade.repository;

import java.util.List;

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
}
