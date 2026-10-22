package com.jiseong.homesense.common.logging;

import org.springframework.boot.json.JsonWriter.Members;
import org.springframework.boot.json.JsonWriter.ValueProcessor;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * COM-LOG-01. 구조화 로그(logstash JSON)의 모든 문자열 값에 {@link LogMasking}을 적용한다 — 메시지와 {@code stack_trace},
 * key-value가 같은 지점을 지나므로 예외 메시지에 섞인 이메일도 스택트레이스까지 함께 가려진다.
 *
 * <p>{@code logging.structured.json.customizer}로 등록한다(application.properties). 구조화 로깅을 끄고 일반 패턴 로그로
 * 바꾸면 이 마스킹이 적용되지 않는다 — 그때는 패턴 변환기 등 다른 지점을 정해야 한다.
 */
public class LogMaskingJsonMembersCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    @Override
    public void customize(Members<ILoggingEvent> members) {
        members.applyingValueProcessor(ValueProcessor.of(String.class, LogMasking::mask));
    }
}
