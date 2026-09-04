package com.jiseong.homesense.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.json.JsonMapper;

/**
 * pageMeta가_null이면/있으면 두 테스트는 이 record가 application.properties의
 * {@code spring.jackson.default-property-inclusion=non_null} 설정 아래서 실제로 어떻게 직렬화되는지를
 * 검증한다 — 그 설정을 그대로 재현한 별도 JsonMapper를 써서, 전체 스프링 컨텍스트 없이도(그리고
 * src/test/resources/application.properties가 main의 application.properties를 가리는 문제 없이도)
 * 검증한다.
 */
class ApiResponseTest {

    private static final JsonMapper NON_NULL_MAPPER = JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    @Test
    void success_data는_성공_응답이고_pageMeta와_error는_없다() {
        ApiResponse<String> response = ApiResponse.success("결과");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("결과");
        assertThat(response.pageMeta()).isNull();
        assertThat(response.error()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void success_Page는_content를_data로_PageMeta를_함께_채운다() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5);

        ApiResponse<List<String>> response = ApiResponse.success(page);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsExactly("a", "b");
        assertThat(response.pageMeta()).isEqualTo(new PageMeta(0, 2, 5, 3));
        assertThat(response.error()).isNull();
    }

    @Test
    void error_code_message는_실패_응답이고_data와_pageMeta는_없다() {
        ApiResponse<Void> response = ApiResponse.error("NOT_FOUND", "존재하지 않습니다");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.pageMeta()).isNull();
        assertThat(response.error().code()).isEqualTo("NOT_FOUND");
        assertThat(response.error().message()).isEqualTo("존재하지 않습니다");
        assertThat(response.error().fieldErrors()).isNull();
    }

    @Test
    void error_fieldErrors는_필드별_에러_목록을_담는다() {
        List<ApiResponse.FieldError> fieldErrors = List.of(new ApiResponse.FieldError("name", "필수입니다"));

        ApiResponse<Void> response = ApiResponse.error("VALIDATION_FAILED", "입력값이 유효하지 않습니다", fieldErrors);

        assertThat(response.error().fieldErrors()).containsExactly(new ApiResponse.FieldError("name", "필수입니다"));
    }

    @Test
    void pageMeta가_null이면_NON_NULL_직렬화_설정에서_JSON에서_빠진다() {
        ApiResponse<String> response = ApiResponse.success("결과");

        String json = NON_NULL_MAPPER.writeValueAsString(response);

        assertThat(json).doesNotContain("pageMeta");
    }

    @Test
    void pageMeta가_있으면_NON_NULL_직렬화_설정에서도_JSON에_포함된다() {
        PageImpl<String> page = new PageImpl<>(List.of("a"), PageRequest.of(0, 1), 1);
        ApiResponse<List<String>> response = ApiResponse.success(page);

        String json = NON_NULL_MAPPER.writeValueAsString(response);

        assertThat(json).contains("\"pageMeta\"");
    }
}
