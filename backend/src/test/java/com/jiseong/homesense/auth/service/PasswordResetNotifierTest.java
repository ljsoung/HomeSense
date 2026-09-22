package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.common.config.FrontendProperties;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.mail.MailSender;

/**
 * {@code @Async}는 Spring AOP 프록시가 있어야만 적용되므로(SVC-SEARCH-01.record()와 같은 이유), 이
 * 단위 테스트는 프록시 없이 메서드를 직접 호출해 로직(토큰 발급, 메일 본문 구성, 실패 시 예외 흡수)만
 * 검증한다 — 실제로 별도 스레드에서 실행되는지는 Spring 프레임워크 자체 기능으로 신뢰한다.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetNotifierTest {

    @Mock
    private PasswordResetTokenService tokenService;
    @Mock
    private MailSender mailSender;
    @Mock
    private AuditLogger auditLogger;

    private final FrontendProperties frontendProperties = new FrontendProperties("http://localhost:5173");

    private PasswordResetNotifier notifier;

    @Test
    void 토큰을_발급하고_재설정_링크가_포함된_메일을_발송한다() {
        notifier = new PasswordResetNotifier(tokenService, mailSender, frontendProperties, auditLogger);
        when(tokenService.issueToken(1L)).thenReturn("raw-token-value");

        notifier.notifyAsync(1L, "user@test.com");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(eq("user@test.com"), anyString(), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("http://localhost:5173/password-reset?token=raw-token-value");
        verify(auditLogger, never()).logPasswordResetMailFailure(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 메일_발송이_실패하면_예외를_삼키고_감사_로그만_남긴다() {
        notifier = new PasswordResetNotifier(tokenService, mailSender, frontendProperties, auditLogger);
        when(tokenService.issueToken(1L)).thenReturn("raw-token-value");
        RuntimeException sesFailure = new RuntimeException("SES sandbox rejection");
        org.mockito.Mockito.doThrow(sesFailure).when(mailSender).send(anyString(), anyString(), anyString());

        notifier.notifyAsync(1L, "user@test.com");

        verify(auditLogger).logPasswordResetMailFailure(1L, sesFailure);
    }
}
