package com.jiseong.homesense.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AuditLoggerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AuditLogger auditLogger = new AuditLogger();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AuditLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private static TradeDraft draft(String datasetId, String sggCd, String umdNm, String jibun, String buildingName) {
        return new TradeDraft(
                HousingType.APT,
                DealCategory.SALE,
                null,
                datasetId,
                sggCd,
                umdNm,
                buildingName,
                jibun,
                new BigDecimal("84.99"),
                (short) 10,
                (short) 2005,
                LocalDate.of(2024, 1, 15),
                120000L,
                null,
                null,
                null,
                "AGENT",
                "강남구",
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void logBatchFailure는_CRITICAL_레벨로_컨텍스트와_예외_정보를_JSON으로_남긴다() {
        RuntimeException e = new RuntimeException("서비스키 오류(resultCode=30)가 3회 연속 발생");

        auditLogger.logBatchFailure("BAT-SCH-01", e);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy().getClassName()).isEqualTo(RuntimeException.class.getName());

        JsonNode json = JSON.readTree(event.getFormattedMessage());
        assertThat(json.path("level").asString()).isEqualTo("CRITICAL");
        assertThat(json.path("event").asString()).isEqualTo("BATCH_FAILURE");
        assertThat(json.path("context").asString()).isEqualTo("BAT-SCH-01");
        assertThat(json.path("exceptionType").asString()).isEqualTo(RuntimeException.class.getName());
        assertThat(json.path("message").asString()).contains("resultCode=30");
        assertThat(json.path("timestamp").asString()).isNotBlank();
    }

    @Test
    void logMatchingFailure는_WARN_레벨로_draft_식별_정보와_사유를_JSON으로_남긴다() {
        TradeDraft draft = draft("15126468", "11680", "역삼동", "123-4", "역삼래미안");

        auditLogger.logMatchingFailure(draft, "지번 불일치 및 단지명 유사도 임계치 미달");

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);

        JsonNode json = JSON.readTree(event.getFormattedMessage());
        assertThat(json.path("level").asString()).isEqualTo("WARN");
        assertThat(json.path("event").asString()).isEqualTo("MATCHING_FAILURE");
        assertThat(json.path("datasetId").asString()).isEqualTo("15126468");
        assertThat(json.path("sggCd").asString()).isEqualTo("11680");
        assertThat(json.path("umdNm").asString()).isEqualTo("역삼동");
        assertThat(json.path("jibun").asString()).isEqualTo("123-4");
        assertThat(json.path("buildingName").asString()).isEqualTo("역삼래미안");
        assertThat(json.path("reason").asString()).isEqualTo("지번 불일치 및 단지명 유사도 임계치 미달");
    }

    @Test
    void logGeocodingFailure는_WARN_레벨로_주소와_사유를_JSON으로_남긴다() {
        auditLogger.logGeocodingFailure("서울특별시 강남구 역삼동 123-4", "카카오맵 좌표 응답 없음");

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);

        JsonNode json = JSON.readTree(event.getFormattedMessage());
        assertThat(json.path("level").asString()).isEqualTo("WARN");
        assertThat(json.path("event").asString()).isEqualTo("GEOCODING_FAILURE");
        assertThat(json.path("address").asString()).isEqualTo("서울특별시 강남구 역삼동 123-4");
        assertThat(json.path("reason").asString()).isEqualTo("카카오맵 좌표 응답 없음");
    }
}
