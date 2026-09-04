package com.jiseong.homesense.common.logging;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * COM-LOG-01. 배치 실패(BAT-ERR-01)·매칭 실패(BAT-MAT-02)·지오코딩 실패(BAT-GEO-01)·예상치 못한
 * 예외(COM-EXC-01)를 SLF4J를 통해 구조화(JSON) 로그로 남긴다(NFR-10). batch_log 테이블 기록과는
 * 별개의 관측 경로다 — 여기서는 로그 레벨에서만 관리하는 상세(스택트레이스 등)를 담고, batch_log에는
 * 담지 않는다.
 *
 * <p>이 프로젝트는 아직 logstash-logback-encoder 같은 JSON 인코더를 logback에 붙이지 않았으므로,
 * 로그 라인 자체를 JSON 문자열로 직렬화해 SLF4J에 넘긴다 — 후속으로 JSON 인코더를 도입해도 이 클래스의
 * 호출부(메서드 시그니처)는 바뀌지 않는다.
 */
@Slf4j
@Component
public class AuditLogger {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    /**
     * CRITICAL 레벨 — 전체 배치 중단급 오류(예: 서비스키 만료로 인한 ABORT_BATCH). 향후 Slack/이메일
     * 알림 연동 여지를 남기되, 현재 범위는 로그 기록까지만 수행한다.
     */
    public void logBatchFailure(String context, Throwable e) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("level", "CRITICAL");
        event.put("event", "BATCH_FAILURE");
        event.put("context", context);
        event.put("exceptionType", e.getClass().getName());
        event.put("message", e.getMessage());
        event.put("timestamp", Instant.now().toString());
        log.error(OBJECT_MAPPER.writeValueAsString(event), e);
    }

    /** 단지/법정동 매칭 실패 기록(BAT-MAT-02). */
    public void logMatchingFailure(TradeDraft draft, String reason) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("level", "WARN");
        event.put("event", "MATCHING_FAILURE");
        event.put("datasetId", draft.datasetId());
        event.put("sggCd", draft.sggCd());
        event.put("umdNm", draft.umdNm());
        event.put("jibun", draft.jibun());
        event.put("buildingName", draft.buildingName());
        event.put("reason", reason);
        event.put("timestamp", Instant.now().toString());
        log.warn(OBJECT_MAPPER.writeValueAsString(event));
    }

    /** 지오코딩 실패 기록(BAT-GEO-01). */
    public void logGeocodingFailure(String address, String reason) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("level", "WARN");
        event.put("event", "GEOCODING_FAILURE");
        event.put("address", address);
        event.put("reason", reason);
        event.put("timestamp", Instant.now().toString());
        log.warn(OBJECT_MAPPER.writeValueAsString(event));
    }
}
