package com.jiseong.homesense.auth.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.common.config.FrontendProperties;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.mail.MailSender;

import lombok.RequiredArgsConstructor;

/**
 * SVC-AUTH-01.requestPasswordReset()가 위임하는 토큰 발급+이메일 발송. {@link AuthService}가 이
 * 클래스를 직접 호출하지 않고 별도 빈으로 분리한 이유는 {@code @Async}가 self-invocation(같은 클래스
 * 내부 호출)에서는 AOP 프록시를 거치지 않아 무력화되기 때문이다 — SVC-RCV-01의
 * {@code ComplexDetailCache} 분리, SVC-SEARCH-01의 {@code SearchService.record()}와 같은 이유
 * (CLAUDE.md 캐싱/SVC-SEARCH-01 절 참고).
 *
 * <p>{@code @Async}로 실행하는 이유는 두 가지다. (1) NFR — SES 네트워크 호출(특히 샌드박스 상태라
 * 재시도·지연이 잦을 수 있다)이 API 응답 경로를 블로킹하면 안 된다. (2) 보안 — 이 메서드는 실제
 * 계정이 존재할 때만 호출되므로, 동기 호출이었다면 "느린 SES 응답으로 인한 응답 지연"이 존재하지 않는
 * 이메일(호출 자체가 없어 즉시 응답)과 존재하는 이메일(SES 호출로 응답이 느려짐)을 구분하는 타이밍
 * 사이드채널이 됐을 것이다 — 비동기로 분리하면 API 응답은 계정 존재 여부와 무관하게 항상 즉시
 * 반환된다.
 *
 * <p>메일 발송 실패(SES 예외 포함)는 삼키고 감사 로그만 남긴다 — 사용자에게는 어떤 경우에도 동일한
 * 성공 응답만 보이므로(계정 존재 여부·발송 성패를 노출하지 않는다는 AUTH-03 설계 원칙), 이 로그가
 * 실제로 이메일이 나갔는지 확인할 수 있는 유일한 지점이다.
 */
@Component
@RequiredArgsConstructor
class PasswordResetNotifier {

    private static final String SUBJECT = "[HomeSense] 비밀번호 재설정 안내";

    private final PasswordResetTokenService tokenService;
    private final MailSender mailSender;
    private final FrontendProperties frontendProperties;
    private final AuditLogger auditLogger;

    @Async
    void notifyAsync(Long userId, String email) {
        String rawToken = tokenService.issueToken(userId);
        String link = frontendProperties.baseUrl() + "/password-reset?token=" + rawToken;
        try {
            mailSender.send(email, SUBJECT, htmlBody(link));
        } catch (RuntimeException e) {
            auditLogger.logPasswordResetMailFailure(userId, e);
        }
    }

    private String htmlBody(String link) {
        return "<p>안녕하세요, HomeSense입니다.</p>"
                + "<p>아래 링크를 클릭해 비밀번호를 재설정해주세요. 이 링크는 30분간 유효합니다.</p>"
                + "<p><a href=\"" + link + "\">" + link + "</a></p>"
                + "<p>본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.</p>";
    }
}
