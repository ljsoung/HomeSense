package com.jiseong.homesense.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 여기서는 로그 레코드가 SLF4J key-value pair로 올바른 구조화 필드를 담아 나가는지만 검증한다.
 * 그 key-value pair를 로그 라인 전체(타임스탬프·레벨·메시지·스택트레이스 포함)를 아우르는 JSON
 * 오브젝트로 렌더링하는 책임은 이 클래스가 아니라 Spring Boot 내장 구조화 로깅
 * (application.properties의 {@code logging.structured.format.console=logstash})에 있고, 그 렌더링
 * 자체는 이미 검증된 프레임워크 코드라 여기서 다시 파싱·검증하지 않는다.
 */
class AuditLoggerTest {

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
    void logBatchFailure는_ERROR_레벨로_CRITICAL_구조화_필드와_원인_예외를_남긴다() {
        RuntimeException e = new RuntimeException("서비스키 오류(resultCode=30)가 3회 연속 발생");

        auditLogger.logBatchFailure("BAT-SCH-01", e);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy().getClassName()).isEqualTo(RuntimeException.class.getName());
        assertThat(event.getKeyValuePairs())
                .extracting(kv -> kv.key, kv -> kv.value)
                .contains(
                        tuple("auditEvent", "BATCH_FAILURE"),
                        tuple("auditSeverity", "CRITICAL"),
                        tuple("context", "BAT-SCH-01"));
    }

    @Test
    void logMatchingFailure는_WARN_레벨로_draft_식별_정보와_사유를_구조화_필드로_남긴다() {
        TradeDraft draft = draft("15126468", "11680", "역삼동", "123-4", "역삼래미안");

        auditLogger.logMatchingFailure(draft, "지번 불일치 및 단지명 유사도 임계치 미달");

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getKeyValuePairs())
                .extracting(kv -> kv.key, kv -> kv.value)
                .contains(
                        tuple("auditEvent", "MATCHING_FAILURE"),
                        tuple("datasetId", "15126468"),
                        tuple("sggCd", "11680"),
                        tuple("umdNm", "역삼동"),
                        tuple("jibun", "123-4"),
                        tuple("buildingName", "역삼래미안"),
                        tuple("reason", "지번 불일치 및 단지명 유사도 임계치 미달"));
    }

    @Test
    void logGeocodingFailure는_WARN_레벨로_주소와_사유를_구조화_필드로_남긴다() {
        auditLogger.logGeocodingFailure("서울특별시 강남구 역삼동 123-4", "카카오맵 좌표 응답 없음");

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getKeyValuePairs())
                .extracting(kv -> kv.key, kv -> kv.value)
                .contains(
                        tuple("auditEvent", "GEOCODING_FAILURE"),
                        tuple("address", "서울특별시 강남구 역삼동 123-4"),
                        tuple("reason", "카카오맵 좌표 응답 없음"));
    }
}
