package com.jiseong.homesense.common.logging;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;

import lombok.extern.slf4j.Slf4j;

/**
 * COM-LOG-01. 배치 실패(BAT-ERR-01)·매칭 실패(BAT-MAT-02)·지오코딩 실패(BAT-GEO-01)·예상치 못한
 * 예외(COM-EXC-01)·Refresh Token 재사용 탐지(SVC-AUTH-01)를 SLF4J를 통해 구조화(JSON) 로그로
 * 남긴다(NFR-10). batch_log 테이블 기록과는
 * 별개의 관측 경로다 — 여기서는 로그 레벨에서만 관리하는 상세(스택트레이스 등)를 담고, batch_log에는
 * 담지 않는다.
 *
 * <p>구조화 필드는 메시지 문자열에 직접 JSON을 끼워 넣지 않고 SLF4J 2.x fluent API의
 * {@code addKeyValue()}로 붙인다 — 로그 레코드 전체(타임스탬프·레벨·로거명·메시지·key-value·
 * 스택트레이스)를 하나의 JSON 오브젝트로 렌더링하는 책임은 로깅 인코더 쪽(application.properties의
 * {@code logging.structured.format.console=logstash}, Spring Boot 내장 구조화 로깅 — 별도 인코더
 * 라이브러리 불필요)에 있다. 이 클래스가 메시지 본문만 JSON 문자열로 만들어 버리면, 그 메시지를
 * 감싸는 타임스탬프/레벨 접두사나 예외 스택트레이스까지 함께 JSON으로 묶이지 않는 한(=구조화 로깅
 * 설정이 없는 한) 로그 라인 전체는 유효한 JSON이 되지 않는다 — 코드리뷰에서 지적된 문제.
 */
@Slf4j
@Component
public class AuditLogger {

    /**
     * CRITICAL 레벨 — 전체 배치 중단급 오류(예: 서비스키 만료로 인한 ABORT_BATCH). 향후 Slack/이메일
     * 알림 연동 여지를 남기되, 현재 범위는 로그 기록까지만 수행한다.
     */
    public void logBatchFailure(String context, Throwable e) {
        log.atError()
                .addKeyValue("auditEvent", "BATCH_FAILURE")
                .addKeyValue("auditSeverity", "CRITICAL")
                .addKeyValue("context", context)
                .setCause(e)
                .log("BATCH_FAILURE context={}", context);
    }

    /** 단지/법정동 매칭 실패 기록(BAT-MAT-02). */
    public void logMatchingFailure(TradeDraft draft, String reason) {
        log.atWarn()
                .addKeyValue("auditEvent", "MATCHING_FAILURE")
                .addKeyValue("datasetId", draft.datasetId())
                .addKeyValue("sggCd", draft.sggCd())
                .addKeyValue("umdNm", draft.umdNm())
                .addKeyValue("jibun", draft.jibun())
                .addKeyValue("buildingName", draft.buildingName())
                .addKeyValue("reason", reason)
                .log("MATCHING_FAILURE reason={}", reason);
    }

    /** 지오코딩 실패 기록(BAT-GEO-01). */
    public void logGeocodingFailure(String address, String reason) {
        log.atWarn()
                .addKeyValue("auditEvent", "GEOCODING_FAILURE")
                .addKeyValue("address", address)
                .addKeyValue("reason", reason)
                .log("GEOCODING_FAILURE reason={}", reason);
    }

    /**
     * Refresh Token 재사용 탐지(SVC-AUTH-01.refreshAccessToken()) — 이미 폐기된(rotation으로
     * 교체됐거나 로그아웃된) Refresh Token으로 재발급이 시도되면 토큰 탈취의 강한 신호로 본다.
     * 응답에는 만료/미존재 토큰과 동일한 401만 노출해 공격자에게 탐지 사실을 드러내지 않으므로,
     * 이 로그가 유일한 관측 지점이다 — 시각은 구조화 로깅이 레코드마다 자동으로 남기므로 별도
     * 필드로 중복 기록하지 않는다.
     */
    public void logRefreshTokenReuseDetected(Long userId) {
        log.atWarn()
                .addKeyValue("auditEvent", "REFRESH_TOKEN_REUSE_DETECTED")
                .addKeyValue("auditSeverity", "HIGH")
                .addKeyValue("userId", userId)
                .log("REFRESH_TOKEN_REUSE_DETECTED userId={}", userId);
    }
}
