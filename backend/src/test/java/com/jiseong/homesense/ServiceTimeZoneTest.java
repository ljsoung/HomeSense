package com.jiseong.homesense;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/** main()이 고정하는 JVM 기본 시간대와 JSON 직렬화 시간대(spring.jackson.time-zone)가 같은지 본다. */
class ServiceTimeZoneTest {

    @Test
    void JVM_기본_시간대와_Jackson_시간대가_같다() throws IOException {
        Properties main = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
            main.load(reader);
        }

        assertThat(HomeSenseApplication.SERVICE_TIME_ZONE).isEqualTo("Asia/Seoul");
        assertThat(main.getProperty("spring.jackson.time-zone")).isEqualTo(HomeSenseApplication.SERVICE_TIME_ZONE);
    }
}
