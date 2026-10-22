package com.jiseong.homesense;

import java.util.TimeZone;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HomeSenseApplication {

    /** 서비스 기준 시간대. auditing(created_at/updated_at)·LocalDateTime.now()·@Scheduled가 모두 JVM 기본 시간대를 따른다. */
    static final String SERVICE_TIME_ZONE = "Asia/Seoul";

    public static void main(String[] args) {
        // JVM 옵션(-Duser.timezone)이나 컨테이너 TZ에 기대지 않고 코드로 고정한다 — UTC가 기본인 서버에 그대로 올리면
        // withdrawn_at(KST Clock)과 created_at(JVM 기본)이 9시간 어긋나고, 알림 이력 6개월 경계도 밀린다.
        TimeZone.setDefault(TimeZone.getTimeZone(SERVICE_TIME_ZONE));

        // backend/.env에 있는 로컬 시크릿을 시스템 프로퍼티로 주입한다.
        // spring-dotenv는 Spring Boot 4의 SpringApplicationRunListener 시그니처 변경과
        // 바이너리 비호환이라 조용히 아무 것도 하지 않으므로 dotenv-java를 직접 호출한다.
        Dotenv.configure()
                .ignoreIfMissing()
                .load()
                .entries()
                .forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        SpringApplication.run(HomeSenseApplication.class, args);
    }

}
