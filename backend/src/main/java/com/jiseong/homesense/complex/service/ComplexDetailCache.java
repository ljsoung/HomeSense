package com.jiseong.homesense.complex.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.common.cache.CacheNames;
import com.jiseong.homesense.common.exception.ComplexNotFoundException;
import com.jiseong.homesense.complex.dto.ComplexDetailResponse;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.trade.entity.MatchMethod;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

import lombok.RequiredArgsConstructor;

/**
 * complexDetailV3::{complexId} 캐시(TTL 24h)만 떼어낸 별도 빈이다. ComplexService.getDetail()이 매
 * 호출마다 SVC-RCV-01.record()를 실행해야 하는데(설계서 3.6절), 그 메서드 자체에
 * {@code @Cacheable}을 걸어두면 record() 호출까지 캐시 히트마다 스킵된다. 그렇다고 같은 클래스
 * 안에 캐시 전용 메서드를 따로 둬도 self-invocation(자기 자신 호출은 프록시를 거치지 않는 Spring
 * AOP의 알려진 제약)에 걸려 캐싱 자체가 무력화되므로, 이 로직만 별도 빈으로 분리해 ComplexService가
 * 외부 빈 호출로 이 메서드를 부르게 한다(CLAUDE.md SVC-RCV-01 절 참고).
 *
 * <p>캐시 이름은 DTO 필드가 바뀔 때마다 올린다. V2는 housingType 추가(SVC-RCV-01), V3는 matchMethod 추가(DTL-01
 * 정밀/근사 배지)다. Redis가 배포 사이에도 살아남는 환경에서 옛 이름으로 저장된 엔트리를 그대로 읽으면, 새로
 * 추가된 필드가 역직렬화 시 조용히 null로 채워진다(Jackson record 역직렬화는 누락 필드를 예외 없이 null로
 * 채운다) — V2 때는 RecentViewService.record()가 housingType null을 보고 기록을 스킵했고(Codex 코드리뷰 지적),
 * V3에서는 모든 단지가 배지 없이 보였을 것이다. 이름을 바꿔 옛 엔트리를 애초에 다시 읽지 않게 한다. 옛 이름의
 * 엔트리는 아무도 참조하지 않아 각자의 TTL로 자연 만료된다. 이 DTO 필드가 다시 바뀌면 이 이름을 또 올려라.
 *
 * <p>matchMethod는 대표 거래에서 온다 — 거래가 새로 적재되거나(BAT-LOD-01) 재매칭으로 다른 단지로 옮겨지거나 매칭
 * 방식이 바뀌면(TradeRematchRunner) 대표 거래가 바뀔 수 있는데, 두 경로 모두 커밋 뒤 TradeCacheEvictionEvent로 이
 * 캐시 항목을 evict한다. 그래서 캐시에 함께 담아도 오래된 배지가 남지 않는다. trade의 complex_id·match_method를
 * 바꾸는 경로를 새로 만들면 그 경로도 이 이벤트를 발행해야 한다.
 */
@Component
@RequiredArgsConstructor
public class ComplexDetailCache {

    private final ComplexRepository complexRepository;
    private final TradeRepository tradeRepository;

    @Cacheable(cacheNames = CacheNames.COMPLEX_DETAIL, key = "#complexId")
    public ComplexDetailResponse get(Long complexId) {
        Complex complex = complexRepository.findById(complexId).orElseThrow(ComplexNotFoundException::new);
        // 카드(ComplexSummaryResponse)와 같은 대표 거래 선정 함수를 쓴다 — 동률 판정(MAX(deal_date) → MAX(trade_id))을
        // 두 곳에서 따로 구현하면 같은 단지의 배지가 화면마다 달라질 수 있다.
        Trade representativeTrade = tradeRepository.findRecentTradesByComplexIds(List.of(complexId)).get(complexId);
        MatchMethod matchMethod = representativeTrade == null ? null : representativeTrade.getMatchMethod();
        return ComplexDetailResponse.from(complex, matchMethod);
    }
}
