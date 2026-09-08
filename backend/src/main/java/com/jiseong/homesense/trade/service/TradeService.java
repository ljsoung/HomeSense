package com.jiseong.homesense.trade.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.trade.dto.DealTypeFilter;
import com.jiseong.homesense.trade.dto.TradeDetailResponse;
import com.jiseong.homesense.trade.dto.TradeResponse;
import com.jiseong.homesense.trade.dto.TradeSearchCondition;
import com.jiseong.homesense.trade.dto.TradeSummaryResponse;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.exception.MissingComplexIdException;
import com.jiseong.homesense.trade.exception.TradeNotFoundException;
import com.jiseong.homesense.trade.repository.TradeRepository;

import lombok.RequiredArgsConstructor;

/**
 * SVC-TRD-01. 지역·조건 기반 실거래 목록 검색(search), 단지 기준 이력 조회(getHistory), 개별 거래
 * 상세(getDetail)를 담당한다. 배치 직후 변경 가능성이 있어 캐시를 적용하지 않는다(CLAUDE.md 캐싱
 * 절 — "거래 검색/이력 조회는 캐시를 적용하지 않는다").
 *
 * <p>getHistory()는 설계서 표에는 {@code getHistory(Long complexId, DealTypeFilter dealType)}
 * 두 인자만 있지만, TradeController가 시그니처로 받는 housingType 파라미터를 버리지 않고 그대로
 * 필터에 반영하도록 세 번째 인자로 확장했다 — Controller 표에는 있고 Service 표에는 빠진
 * 불일치라, 후속 검토 시 지성 확인이 필요하다(SVC-CPX-01 사례처럼 CLAUDE.md 결정 표에 반영 권장).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeService {

    private final TradeRepository tradeRepository;

    public Page<TradeSummaryResponse> search(TradeSearchCondition condition, Pageable pageable) {
        return tradeRepository.search(condition, pageable);
    }

    public List<TradeResponse> getHistory(Long complexId, HousingType housingType, DealTypeFilter dealType) {
        if (complexId == null) {
            throw new MissingComplexIdException();
        }
        return tradeRepository.findHistory(complexId, housingType, dealType).stream()
                .map(TradeResponse::of)
                .toList();
    }

    public TradeDetailResponse getDetail(Long tradeId) {
        Trade trade = tradeRepository.findById(tradeId).orElseThrow(TradeNotFoundException::new);
        return TradeDetailResponse.from(trade);
    }
}
