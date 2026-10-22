package com.jiseong.homesense.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** COM-SEC-01. 실제 Security 필터 체인에서 허용 출처의 preflight만 통과하고, 자격 증명(쿠키) 허용은 켜지 않는다. */
@SpringBootTest(properties = "homesense.cors.allowed-origins=https://hmss.site,https://www.hmss.site")
@AutoConfigureMockMvc
class CorsFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 허용_출처의_preflight는_인증이_필요한_경로여도_200이고_Authorization_헤더를_허용한다() throws Exception {
        MvcResult result = mockMvc.perform(options("/api/users/me")
                        .header(HttpHeaders.ORIGIN, "https://www.hmss.site")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,x-session-id"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://www.hmss.site"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase("authorization").containsIgnoringCase("x-session-id");
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("PUT");
    }

    @Test
    void 허용하지_않은_출처의_preflight는_403이다() throws Exception {
        mockMvc.perform(options("/api/complexes/popular")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void 허용하지_않은_헤더를_요청하면_403이다() throws Exception {
        mockMvc.perform(options("/api/complexes/popular")
                        .header(HttpHeaders.ORIGIN, "https://hmss.site")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-unknown"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 실제_요청에도_허용_출처_헤더가_붙는다() throws Exception {
        mockMvc.perform(get("/api/no-such-path").header(HttpHeaders.ORIGIN, "https://hmss.site"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://hmss.site"));
    }
}
