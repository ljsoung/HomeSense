package com.jiseong.homesense.batch.loader;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

import lombok.RequiredArgsConstructor;

/**
 * TradeChunkLoader.upsertOne()의 신규 INSERT 시도만 별도 REQUIRES_NEW 트랜잭션(별도 EntityManager)에서
 * 실행한다. JPA 스펙상 flush() 실패는(dedup_hash UNIQUE 충돌 포함) 현재 트랜잭션을 rollback-only로
 * 표시한다 — 이는 MariaDB가 문장 단위 실패로 트랜잭션 전체를 중단시키지 않는 것과는 별개로, JPA 구현체
 * (Hibernate)가 스펙에 따라 강제하는 동작이다. INSERT를 청크 트랜잭션과 같은 EntityManager에서 시도하면,
 * 충돌이 나는 순간 그 청크 트랜잭션 전체가 커밋 시점에 UnexpectedRollbackException으로 롤백되어
 * 이미 처리된 최대 499건까지 함께 유실된다(코드리뷰에서 지적된 실제 결함). REQUIRES_NEW로 분리하면
 * 이 INSERT가 실패해도 롤백되는 트랜잭션·EntityManager는 이 메서드 안에서 새로 만든 것뿐이라,
 * TradeChunkLoader.loadChunk()의 청크 트랜잭션은 오염되지 않고 그대로 재시도(UPDATE)를 진행할 수 있다.
 */
@Component
@RequiredArgsConstructor
class TradeInsertGateway {

    private final TradeRepository tradeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(Trade trade) {
        tradeRepository.saveAndFlush(trade);
    }
}
