package com.jiseong.homesense.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageMetaTest {

    @Test
    void Page로부터_page_size_totalElements_totalPages를_추출한다() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageMeta meta = PageMeta.from(page);

        assertThat(meta.page()).isEqualTo(1);
        assertThat(meta.size()).isEqualTo(2);
        assertThat(meta.totalElements()).isEqualTo(5);
        assertThat(meta.totalPages()).isEqualTo(3);
    }
}
