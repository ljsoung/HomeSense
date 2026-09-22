package com.jiseong.homesense.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.common.config.AwsSesProperties;

import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

@ExtendWith(MockitoExtension.class)
class SesMailSenderTest {

    @Mock
    private SesClient sesClient;

    private final AwsSesProperties awsSesProperties =
            new AwsSesProperties("ap-northeast-2", "sender@test.com", null, null);

    private SesMailSender mailSender;

    @Test
    void 수신자_제목_본문을_그대로_담아_SES에_발송을_요청한다() {
        mailSender = new SesMailSender(sesClient, awsSesProperties);
        when(sesClient.sendEmail(org.mockito.ArgumentMatchers.any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("msg-1").build());

        mailSender.send("user@test.com", "제목", "<p>본문</p>");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        SendEmailRequest request = captor.getValue();
        assertThat(request.source()).isEqualTo("sender@test.com");
        assertThat(request.destination().toAddresses()).containsExactly("user@test.com");
        assertThat(request.message().subject().data()).isEqualTo("제목");
        assertThat(request.message().body().html().data()).isEqualTo("<p>본문</p>");
    }

    @Test
    void SES_예외는_가공_없이_그대로_전파한다() {
        mailSender = new SesMailSender(sesClient, awsSesProperties);
        SesException sandboxRejection = (SesException) SesException.builder().message("not verified").build();
        when(sesClient.sendEmail(org.mockito.ArgumentMatchers.any(SendEmailRequest.class)))
                .thenThrow(sandboxRejection);

        assertThatThrownBy(() -> mailSender.send("unverified@test.com", "제목", "본문"))
                .isSameAs(sandboxRejection);
    }
}
