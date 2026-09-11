package com.jiseong.homesense.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.jiseong.homesense.search.dto.PopularKeywordResponse;
import com.jiseong.homesense.search.entity.SearchLog;
import com.jiseong.homesense.search.repository.SearchLogRepository;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private SearchLogRepository searchLogRepository;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(searchLogRepository);
    }

    @Test
    void getPopularKeywords_로그가_없으면_빈_리스트를_반환한다() {
        when(searchLogRepository.findTopKeywordsSince(any(), any())).thenReturn(List.of());

        List<PopularKeywordResponse> result = searchService.getPopularKeywords(5);

        assertThat(result).isEmpty();
    }

    @Test
    void getPopularKeywords_리포지토리_결과를_그대로_매핑한다() {
        when(searchLogRepository.findTopKeywordsSince(any(), any())).thenReturn(List.of("강남구", "역삼동"));

        List<PopularKeywordResponse> result = searchService.getPopularKeywords(5);

        assertThat(result).containsExactly(new PopularKeywordResponse("강남구"), new PopularKeywordResponse("역삼동"));
    }

    /** "최근 7일" 집계 창을 KST 기준으로 계산하는지 검증한다(CLAUDE.md 날짜/시간 처리 원칙). */
    @Test
    void getPopularKeywords_최근_7일_기준_KST로_since를_계산한다() {
        when(searchLogRepository.findTopKeywordsSince(any(), any())).thenReturn(List.of());
        LocalDateTime expected = LocalDateTime.now(KST).minusDays(7);

        searchService.getPopularKeywords(5);

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(searchLogRepository).findTopKeywordsSince(sinceCaptor.capture(), any(Pageable.class));
        assertThat(Duration.between(expected, sinceCaptor.getValue()).abs()).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void getPopularKeywords_limit을_Pageable에_그대로_반영한다() {
        when(searchLogRepository.findTopKeywordsSince(any(), any())).thenReturn(List.of());

        searchService.getPopularKeywords(3);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(searchLogRepository).findTopKeywordsSince(any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void record_keyword가_null이면_저장하지_않는다() {
        searchService.record(null);

        verify(searchLogRepository, never()).save(any());
    }

    @Test
    void record_keyword가_공백이면_저장하지_않는다() {
        searchService.record("   ");

        verify(searchLogRepository, never()).save(any());
    }

    @Test
    void record_keyword를_trim해서_저장한다() {
        searchService.record("  강남구  ");

        ArgumentCaptor<SearchLog> captor = ArgumentCaptor.forClass(SearchLog.class);
        verify(searchLogRepository).save(captor.capture());
        assertThat(captor.getValue().getKeyword()).isEqualTo("강남구");
    }

    /** 로깅 실패가 검색 자체를 실패시키면 안 된다(CLAUDE.md API-SEARCH-01 절 참고). */
    @Test
    void record_저장중_예외가_나도_전파하지_않는다() {
        when(searchLogRepository.save(any())).thenThrow(new RuntimeException("DB 일시 장애"));

        assertThatCode(() -> searchService.record("강남구")).doesNotThrowAnyException();
    }
}
