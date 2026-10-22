package com.jiseong.homesense.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * COM-CFG-01. application-prod.properties를 그대로 얹어 운영 설정을 확인한다 — API 문서 비공개, CORS 필수, 앱 패키지 로그 레벨.
 * DB·Redis 접속값만 테스트용으로 바꾸고, 나머지 {@code ${...}}는 아래 이름으로 채운다(운영 필수 환경변수와 같은 이름).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-prod.properties", properties = {
        "spring.datasource.url=jdbc:h2:mem:homesense-prod-profile;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.data.redis.host=localhost",
        "JWT_SECRET=test-jwt-secret-key-for-context-loading-only-0123456789",
        "DATA_GO_KR_SERVICE_KEY=test-key",
        "KAKAO_API_KEY=test-key",
        "AWS_SES_SENDER_ADDRESS=noreply@example.com",
        "FRONTEND_URL=https://hmss.site",
        "CORS_ALLOWED_ORIGINS=https://hmss.site,https://www.hmss.site"
})
class ProdProfileSettingsTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CorsProperties corsProperties;

    @Test
    void 운영에서는_API_문서를_열지_않는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void 운영은_CORS_허용_출처가_필수이고_환경변수에서_읽는다() {
        assertThat(corsProperties.required()).isTrue();
        assertThat(corsProperties.origins()).containsExactly("https://hmss.site", "https://www.hmss.site");
    }

    @Test
    void 앱_패키지_로그_레벨이_실제_패키지에_적용된다() {
        // 루트 기본값도 INFO라 유효 레벨만 보면 패키지 이름이 틀려도 통과한다 — 패키지 로거에 레벨이 직접 걸렸는지 본다.
        Logger appPackage = (Logger) LoggerFactory.getLogger("com.jiseong.homesense");

        assertThat(appPackage.getLevel()).isEqualTo(Level.INFO);
    }
}
