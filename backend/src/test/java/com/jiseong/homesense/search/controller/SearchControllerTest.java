package com.jiseong.homesense.search.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.search.dto.PopularKeywordResponse;
import com.jiseong.homesense.search.service.SearchService;

/** API-SEARCH-01(신규 제안 — CLAUDE.md 참고). */
@WebMvcTest(controllers = SearchController.class)
@AutoConfigureMockMvc(addFilters = false)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private AuditLogger auditLogger;

    @Test
    void 인기검색어_조회에_성공하면_200과_목록을_반환한다() throws Exception {
        when(searchService.getPopularKeywords(5))
                .thenReturn(List.of(new PopularKeywordResponse("강남구"), new PopularKeywordResponse("역삼동")));

        mockMvc.perform(get("/api/search/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].keyword").value("강남구"))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void 인기검색어_limit_파라미터를_그대로_전달한다() throws Exception {
        when(searchService.getPopularKeywords(3)).thenReturn(List.of(new PopularKeywordResponse("강남구")));

        mockMvc.perform(get("/api/search/popular").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 로그가_없으면_빈_배열을_반환한다() throws Exception {
        when(searchService.getPopularKeywords(5)).thenReturn(List.of());

        mockMvc.perform(get("/api/search/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void limit이_0이면_400을_반환하고_서비스를_호출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/search/popular").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_LIMIT"));

        verify(searchService, never()).getPopularKeywords(anyInt());
    }

    @Test
    void limit이_음수면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/search/popular").param("limit", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_LIMIT"));
    }

    @Test
    void limit이_상한을_넘으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/search/popular").param("limit", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_LIMIT"));

        verify(searchService, never()).getPopularKeywords(anyInt());
    }

    @Test
    void limit이_상한과_같으면_통과한다() throws Exception {
        when(searchService.getPopularKeywords(20)).thenReturn(List.of());

        mockMvc.perform(get("/api/search/popular").param("limit", "20"))
                .andExpect(status().isOk());
    }
}
