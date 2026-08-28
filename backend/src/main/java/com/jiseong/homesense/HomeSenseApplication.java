package com.jiseong.homesense;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HomeSenseApplication {

    public static void main(String[] args) {
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
