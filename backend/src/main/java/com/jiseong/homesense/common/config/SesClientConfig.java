package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.SesClientBuilder;

/**
 * AUTH-03. {@link AwsSesProperties}/{@link FrontendProperties}를 등록하고 {@link SesClient} 빈을
 * 만든다 — build.gradle의 {@code software.amazon.awssdk:ses} 의존성이 처음으로 실제 소비되는 지점이다.
 *
 * <p>accessKey/secretKey가 둘 다 채워져 있으면(로컬 개발·현재 배포 환경) {@link StaticCredentialsProvider}로
 * 고정 자격 증명을 쓰고, 비어 있으면 AWS SDK 기본 자격 증명 체인(환경변수 → 프로파일 → EC2/ECS 인스턴스
 * 역할)에 위임한다 — 나중에 인스턴스 역할 기반 배포로 옮겨가도 이 클래스를 수정할 필요가 없다.
 */
@Configuration
@EnableConfigurationProperties({AwsSesProperties.class, FrontendProperties.class})
public class SesClientConfig {

    @Bean
    public SesClient sesClient(AwsSesProperties props) {
        SesClientBuilder builder = SesClient.builder().region(Region.of(props.region()));
        if (StringUtils.hasText(props.accessKey()) && StringUtils.hasText(props.secretKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey(), props.secretKey())));
        }
        return builder.build();
    }
}
