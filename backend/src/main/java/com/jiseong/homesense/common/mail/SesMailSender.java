package com.jiseong.homesense.common.mail;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.common.config.AwsSesProperties;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

/**
 * {@link MailSender}의 AWS SES 구현체. 이 계정은 아직 SES 프로덕션 액세스가 승인되지 않은 샌드박스
 * 상태라(검증된 수신자에게만 실제 발송 가능) {@link software.amazon.awssdk.services.ses.model.SesException}이
 * 흔히 발생할 수 있다 — 이 클래스는 그 예외를 그대로(가공 없이) 던진다. 어떻게 처리할지(재시도·무시·로깅)는
 * 호출자의 책임이다 — 예를 들어 {@link com.jiseong.homesense.auth.service.PasswordResetNotifier}는 이
 * 예외를 삼키고 감사 로그만 남긴다(응답 경로에 영향을 주지 않기 위해).
 */
@Component
@RequiredArgsConstructor
public class SesMailSender implements MailSender {

    private static final String CHARSET = "UTF-8";

    private final SesClient sesClient;
    private final AwsSesProperties awsSesProperties;

    @Override
    public void send(String to, String subject, String htmlBody) {
        SendEmailRequest request = SendEmailRequest.builder()
                .source(awsSesProperties.senderAddress())
                .destination(Destination.builder().toAddresses(to).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).charset(CHARSET).build())
                        .body(Body.builder()
                                .html(Content.builder().data(htmlBody).charset(CHARSET).build())
                                .build())
                        .build())
                .build();
        sesClient.sendEmail(request);
    }
}
