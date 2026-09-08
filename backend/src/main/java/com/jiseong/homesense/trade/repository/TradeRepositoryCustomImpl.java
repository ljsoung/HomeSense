package com.jiseong.homesense.trade.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.jiseong.homesense.complex.entity.QComplex;
import com.jiseong.homesense.region.entity.QLegalDistrictCode;
import com.jiseong.homesense.trade.dto.DealTypeFilter;
import com.jiseong.homesense.trade.dto.TradeSearchCondition;
import com.jiseong.homesense.trade.dto.TradeSortCondition;
import com.jiseong.homesense.trade.dto.TradeSummaryResponse;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.QTrade;
import com.jiseong.homesense.trade.entity.Trade;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * SVC-TRD-01 search()/findHistory()의 동적 쿼리 구현체. Spring Data JPA가 "인터페이스명 + Impl"
 * 규칙으로 자동 탐지하므로 별도 등록 없이 그대로 위임된다(ComplexRepositoryCustomImpl과 동일 관례).
 */
@Repository
@RequiredArgsConstructor
class TradeRepositoryCustomImpl implements TradeRepositoryCustom {

    private static final QTrade trade = QTrade.trade;
    private static final QComplex complex = QComplex.complex;
    private static final QLegalDistrictCode legalDistrictCode = QLegalDistrictCode.legalDistrictCode;

    private final JPAQueryFactory queryFactory;

    /**
     * SRCH-01(리스트형 보기) — 단지 단위로 대표 거래를 뽑는 ComplexRepositoryCustomImpl.search()와
     * 달리 거래 건 하나하나를 그대로 반환한다. 취소된 거래(cancel_yn=true)는 목록형 검색 결과에서
     * 제외한다 — ComplexRepositoryCustomImpl.tradeFilters()와 같은 원칙(지성 확인 필요: 명시적
     * 확인 전까지는 "검색 결과=유효 매물"이라는 전제로 취소 건을 제외했다).
     */
    @Override
    public Page<TradeSummaryResponse> search(TradeSearchCondition condition, Pageable pageable) {
        BooleanBuilder where = filters(condition);

        List<Trade> content = queryFactory
                .selectFrom(trade)
                .leftJoin(trade.complex, complex).fetchJoin()
                .leftJoin(trade.legalDistrictCode, legalDistrictCode).fetchJoin()
                .where(where)
                // dealDate가 같은 거래가 드물지 않아 tradeId를 확정적 2차 정렬키로 덧붙인다 —
                // ComplexRepositoryCustomImpl.search()의 페이지 경계 안정성 원칙과 동일하다.
                .orderBy(orderSpecifier(condition), trade.tradeId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(trade.count())
                .from(trade)
                .where(where)
                .fetchOne();

        List<TradeSummaryResponse> mapped = content.stream().map(TradeSummaryResponse::of).toList();
        return new PageImpl<>(mapped, pageable, total != null ? total : 0);
    }

    @Override
    public List<Trade> findHistory(Long complexId, HousingType housingType, DealTypeFilter dealType) {
        BooleanBuilder where = new BooleanBuilder().and(trade.complex.complexId.eq(complexId));

        if (housingType != null) {
            where.and(trade.housingType.eq(housingType));
        }
        if (dealType != null && dealType.dealCategory() != null) {
            where.and(trade.dealCategory.eq(dealType.dealCategory()));
        }
        if (dealType != null && dealType.rentType() != null) {
            where.and(trade.rentType.eq(dealType.rentType()));
        }

        return queryFactory
                .selectFrom(trade)
                .where(where)
                .orderBy(trade.dealDate.desc(), trade.tradeId.desc())
                .fetch();
    }

    private BooleanBuilder filters(TradeSearchCondition condition) {
        BooleanBuilder builder = new BooleanBuilder().and(trade.cancelYn.isFalse());

        if (condition.legalDongCd() != null) {
            builder.and(trade.legalDistrictCode.legalDongCd.eq(condition.legalDongCd()));
        }
        if (condition.housingTypes() != null && !condition.housingTypes().isEmpty()) {
            builder.and(trade.housingType.in(condition.housingTypes()));
        }
        if (condition.dealCategory() != null) {
            builder.and(trade.dealCategory.eq(condition.dealCategory()));
        }
        if (condition.rentType() != null) {
            builder.and(trade.rentType.eq(condition.rentType()));
        }
        if (condition.areaMin() != null) {
            builder.and(trade.excluUseArea.goe(condition.areaMin()));
        }
        if (condition.areaMax() != null) {
            builder.and(trade.excluUseArea.loe(condition.areaMax()));
        }

        NumberPath<Long> amountPath = amountPath(condition);
        if (condition.amountMin() != null) {
            builder.and(amountPath.goe(condition.amountMin()));
        }
        if (condition.amountMax() != null) {
            builder.and(amountPath.loe(condition.amountMax()));
        }

        return builder;
    }

    /**
     * RENT면 보증금(depositAmount), 그 외(SALE·미지정)면 매매금액(dealAmount) 기준(TradeSearchCondition
     * 문서 참고). rentType은 dealCategory와 독립적으로 선택 가능한 필드라(레코드 컴포넌트가 서로
     * nullable 여부를 강제하지 않음) rentType만 채워지고 dealCategory가 비어 있는 호출(예:
     * rentType=WOLSE만 지정)도 RENT로 취급해야 한다 — 그렇지 않으면 RENT 행은 dealAmount가 보통
     * NULL이라 금액 범위 필터가 0건을 내고 AMOUNT 정렬도 TradeSummaryResponse가 실제로 노출하는
     * depositAmount가 아니라 dealAmount로 정렬되는 불일치가 생긴다(코드리뷰에서 지적됨).
     */
    private NumberPath<Long> amountPath(TradeSearchCondition condition) {
        boolean isRent = condition.dealCategory() == DealCategory.RENT || condition.rentType() != null;
        return isRent ? trade.depositAmount : trade.dealAmount;
    }

    private OrderSpecifier<?> orderSpecifier(TradeSearchCondition condition) {
        TradeSortCondition sort = condition.sort() != null ? condition.sort() : TradeSortCondition.LATEST;
        return switch (sort) {
            case LATEST -> trade.dealDate.desc();
            case AMOUNT -> amountPath(condition).asc();
            case AREA -> trade.excluUseArea.desc();
        };
    }
}
