package com.jiseong.homesense.complex.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.jiseong.homesense.complex.dto.BoundsCondition;
import com.jiseong.homesense.complex.dto.ComplexSearchCondition;
import com.jiseong.homesense.complex.dto.ComplexSummaryResponse;
import com.jiseong.homesense.complex.dto.MapFilterCondition;
import com.jiseong.homesense.complex.dto.SortCondition;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.entity.QComplex;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.QTrade;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * SVC-CPX-01 search()/searchInBounds()의 동적 쿼리 구현체. Spring Data JPA가 {@code ComplexRepository}가
 * 구현하는 {@code ComplexRepositoryCustom}의 실제 구현체를 "인터페이스명 + Impl" 규칙으로 자동
 * 탐지하므로 이 클래스는 별도 등록 없이 그대로 위임된다.
 */
@Repository
@RequiredArgsConstructor
class ComplexRepositoryCustomImpl implements ComplexRepositoryCustom {

    private static final QComplex complex = QComplex.complex;
    private static final QTrade trade = QTrade.trade;

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<ComplexSummaryResponse> search(ComplexSearchCondition condition, Pageable pageable) {
        QTrade subTrade = new QTrade("subTrade");
        QTrade tieBreakTrade = new QTrade("tieBreakTrade");
        BooleanBuilder complexFilters = complexFilters(condition);
        BooleanBuilder tradeFilters = tradeFilters(condition, trade);
        BooleanBuilder subTradeFilters = tradeFilters(condition, subTrade);
        BooleanBuilder tieBreakTradeFilters = tradeFilters(condition, tieBreakTrade);

        // 검색조건을 만족하는 거래 중 "가장 최근 거래" 하나만 각 단지의 대표 거래로 남긴다.
        // dealDate는 일 단위라 같은 날짜에 여러 건이 동률로 걸리는 게 드물지 않다 — MAX(dealDate)만
        // 걸면 그 동률 거래 전부가 살아남아 같은 complex_id가 결과에 여러 행(=매물 카드 여러 장)으로
        // 중복 노출된다. ComplexSummaryResponse가 "단지 1건 = 카드 1장"을 전제하므로, dealDate가
        // 같을 때는 MAX(trade_id)로 한 번 더 좁혀 항상 정확히 한 건만 남긴다(코드리뷰에서 지적됨 —
        // 처음엔 이 동률 중복을 "드문 엣지케이스"로 문서화하고 넘어갔으나, 대표거래 개념 자체와
        // 충돌한다는 지적을 받아 근본 수정함).
        var maxDealDateSubquery = JPAExpressions
                .select(subTrade.dealDate.max())
                .from(subTrade)
                .where(subTrade.complex.eq(complex).and(subTradeFilters));

        var representativeTradeIdSubquery = JPAExpressions
                .select(tieBreakTrade.tradeId.max())
                .from(tieBreakTrade)
                .where(tieBreakTrade.complex.eq(complex)
                        .and(tieBreakTradeFilters)
                        .and(tieBreakTrade.dealDate.eq(maxDealDateSubquery)));

        BooleanBuilder where = complexFilters.and(tradeFilters).and(trade.tradeId.eq(representativeTradeIdSubquery));

        List<Tuple> rows = queryFactory
                .select(complex, trade)
                .from(complex)
                .join(trade).on(trade.complex.eq(complex))
                .where(where)
                // 대표거래가 단지당 정확히 하나로 좁혀졌더라도, 정렬 기준(금액·면적) 값 자체가 서로
                // 다른 단지 사이에서 동률일 수 있다 — complexId를 확정적 2차 정렬키로 덧붙이지 않으면
                // offset/limit으로 나눠 받는 인접 페이지에서 같은 단지가 중복되거나 빠질 수 있다
                // (코드리뷰에서 지적됨).
                .orderBy(orderSpecifier(condition), complex.complexId.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<ComplexSummaryResponse> content = rows.stream()
                .map(row -> ComplexSummaryResponse.of(row.get(complex), row.get(trade)))
                .toList();

        Long total = queryFactory
                .select(complex.count())
                .from(complex)
                .join(trade).on(trade.complex.eq(complex))
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    @Override
    public List<Complex> searchInBounds(BoundsCondition bounds, MapFilterCondition filter, int limitPlusOne) {
        BooleanBuilder where = new BooleanBuilder()
                .and(complex.latitude.between(bounds.swLat(), bounds.neLat()))
                .and(complex.longitude.between(bounds.swLng(), bounds.neLng()));

        if (filter.housingTypes() != null && !filter.housingTypes().isEmpty()) {
            where.and(JPAExpressions.selectOne()
                    .from(trade)
                    .where(trade.complex.eq(complex)
                            .and(trade.housingType.in(filter.housingTypes()))
                            .and(trade.cancelYn.isFalse()))
                    .exists());
        }

        return queryFactory
                .selectFrom(complex)
                .where(where)
                .limit(limitPlusOne)
                .fetch();
    }

    private BooleanBuilder complexFilters(ComplexSearchCondition condition) {
        BooleanBuilder builder = new BooleanBuilder();
        if (condition.sido() != null) {
            builder.and(complex.sido.eq(condition.sido()));
        }
        if (condition.sigungu() != null) {
            builder.and(complex.sigungu.eq(condition.sigungu()));
        }
        if (condition.dongRi() != null) {
            builder.and(complex.dongRi.eq(condition.dongRi()));
        }

        // UI정의서 4.4절 — 건축년도 필터는 trade.build_year(거래 건별, API 원본이라 매매 이력마다
        // 값이 갈릴 수 있음)가 아니라 complex.approval_date(단지 기본정보 xlsx의 사용승인일, 단지당
        // 값이 하나로 고정)를 기준으로 삼는다 — trade 기준이면 area/amount 조건을 만족하는 거래의
        // build_year가 데이터 오류 등으로 실제 사용승인일과 어긋날 때 그 단지를 잘못 걸러낸다
        // (코드리뷰에서 지적됨).
        if (condition.buildYearMin() != null) {
            builder.and(complex.approvalDate.year().goe(condition.buildYearMin().intValue()));
        }
        if (condition.buildYearMax() != null) {
            builder.and(complex.approvalDate.year().loe(condition.buildYearMax().intValue()));
        }

        return builder;
    }

    private BooleanBuilder tradeFilters(ComplexSearchCondition condition, QTrade t) {
        BooleanBuilder builder = new BooleanBuilder().and(t.cancelYn.isFalse());

        if (condition.housingTypes() != null && !condition.housingTypes().isEmpty()) {
            builder.and(t.housingType.in(condition.housingTypes()));
        }
        if (condition.dealCategory() != null) {
            builder.and(t.dealCategory.eq(condition.dealCategory()));
        }
        if (condition.areaMin() != null) {
            builder.and(t.excluUseArea.goe(condition.areaMin()));
        }
        if (condition.areaMax() != null) {
            builder.and(t.excluUseArea.loe(condition.areaMax()));
        }

        NumberPath<Long> amountPath = amountPath(condition, t);
        if (condition.amountMin() != null) {
            builder.and(amountPath.goe(condition.amountMin()));
        }
        if (condition.amountMax() != null) {
            builder.and(amountPath.loe(condition.amountMax()));
        }

        return builder;
    }

    /** RENT면 보증금(depositAmount), 그 외(SALE·미지정)면 매매금액(dealAmount) 기준(ComplexSearchRequest 문서 참고). */
    private NumberPath<Long> amountPath(ComplexSearchCondition condition, QTrade t) {
        return condition.dealCategory() == DealCategory.RENT ? t.depositAmount : t.dealAmount;
    }

    private OrderSpecifier<?> orderSpecifier(ComplexSearchCondition condition) {
        SortCondition sort = condition.sort() != null ? condition.sort() : SortCondition.LATEST;
        return switch (sort) {
            case LATEST -> trade.dealDate.desc();
            case AMOUNT -> amountPath(condition, trade).asc();
            case AREA -> trade.excluUseArea.desc();
        };
    }
}
