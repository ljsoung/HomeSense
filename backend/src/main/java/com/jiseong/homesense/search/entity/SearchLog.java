package com.jiseong.homesense.search.entity;

import java.time.LocalDateTime;
import java.time.ZoneId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ENT-SEARCH-01(신규 제안, 반영 전 검토 필요 — CLAUDE.md API-SEARCH-01 절 참고). SVC-CPX-01.search()가
 * 검색 실행마다 남기는 원문 키워드 로그다. FK 없음 — 누가 검색했는지는 인기검색어 집계 목적에
 * 불필요하다(CLAUDE.md "사용하지 않는 컬럼은 추가하지 않는다" 원칙과 같은 판단). 향후 주체별 분석이
 * 필요해지면 user_id/session_id 컬럼 추가를 검토한다.
 *
 * <p>searchedAt은 RecentView.viewedAt과 달리 명시적으로 KST로 계산한다 — RecentView.viewedAt은 정렬
 * 용도(최신순)로만 쓰여 서버 타임존이 달라도 상대적 순서만 맞으면 문제가 없지만, searchedAt은
 * {@link com.jiseong.homesense.search.service.SearchService#getPopularKeywords(int)}가 별도로 계산한
 * KST 기준 "최근 7일" 경계값과 직접 비교되는 컬럼이라 두 계산이 서로 다른 타임존을 쓰면(예: 컨테이너
 * 환경의 JVM 기본 타임존이 UTC인 경우) 경계 부근 레코드가 조용히 잘못 집계될 수 있다(CLAUDE.md
 * 날짜/시간 처리 원칙 참고).
 */
@Entity
@Table(name = "search_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchLog {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "search_log_id")
    private Long searchLogId;

    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    private SearchLog(String keyword, LocalDateTime searchedAt) {
        this.keyword = keyword;
        this.searchedAt = searchedAt;
    }

    /** keyword는 호출자(SearchService.record())가 이미 trim + 공백/blank 검증을 마친 값이어야 한다. */
    public static SearchLog record(String keyword) {
        return new SearchLog(keyword, LocalDateTime.now(KST));
    }
}
