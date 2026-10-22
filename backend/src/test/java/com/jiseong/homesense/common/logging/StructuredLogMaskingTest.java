package com.jiseong.homesense.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 실제 구조화 로깅(logstash JSON) 출력에서 이메일이 메시지와 stack_trace 양쪽 모두 가려지는지 본다. 테스트 리소스의
 * application.properties가 main 것을 대신하므로 로깅 설정은 아래 properties로 켜고, main 파일에 같은 설정이 있는지는
 * 따로 확인한다.
 */
@SpringBootTest(properties = {
        "logging.structured.format.console=logstash",
        "logging.structured.json.customizer=com.jiseong.homesense.common.logging.LogMaskingJsonMembersCustomizer"
})
@ExtendWith(OutputCaptureExtension.class)
class StructuredLogMaskingTest {

    private static final Logger log = LoggerFactory.getLogger(StructuredLogMaskingTest.class);
    private static final String EMAIL = "leak.check.7731@example.com";

    @Autowired
    private AuditLogger auditLogger;

    @Test
    void 예외_메시지에_섞인_이메일은_메시지와_스택트레이스_양쪽에서_가려진다(CapturedOutput output) {
        DataIntegrityViolationException e = new DataIntegrityViolationException(
                "could not execute statement [Duplicate entry '" + EMAIL + "' for key 'uk_user_email']");

        auditLogger.logBatchFailure("COM-EXC-01", e);
        log.warn("signup failed for {}", EMAIL);

        String out = output.getOut() + output.getErr();
        assertThat(out).contains("\"stack_trace\"").contains("DataIntegrityViolationException");
        assertThat(out).doesNotContain(EMAIL).doesNotContain("leak.check.7731");
        assertThat(out).contains("Duplicate entry 'l***@e***.com'");
        assertThat(out).contains("signup failed for l***@e***.com");
    }

    @Test
    void Bearer_토큰도_로그에서_가려진다(CapturedOutput output) {
        log.warn("header=Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3In0.sig-123");

        assertThat(output.getOut() + output.getErr()).contains("header=Bearer ***").doesNotContain("eyJzdWIiOiI3In0");
    }

    @Test
    void main_설정에_구조화_로깅과_마스킹_customizer가_있다() throws IOException {
        String main = Files.readString(Path.of("src/main/resources/application.properties"));

        assertThat(main).contains("logging.structured.format.console=logstash")
                .contains("logging.structured.json.customizer=" + LogMaskingJsonMembersCustomizer.class.getName());
    }
}
