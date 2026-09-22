package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * COM-CFG-01. homesense.external.aws.ses.* 설정을 바인딩한다 — AUTH-03(비밀번호 재설정)이 이 프로젝트
 * 최초의 실제 SES 소비자다. build.gradle의 {@code software.amazon.awssdk:ses} 의존성과
 * {@code AWS_SES_ACCESS_KEY}/{@code AWS_SES_SECRET_KEY} 환경변수는 "BAT-MAIL-01" 주석과 함께 이미
 * 선언돼 있었지만, 실제로 이 값을 바인딩하는 {@code @ConfigurationProperties} 클래스도 SesClient 빈도
 * 존재하지 않았다 — CLAUDE.md가 이 사실을 정정해 기록한다(향후 BAT-MAIL-01 구현 시 이 클래스를 그대로
 * 재사용하라).
 *
 * <p>{@code region}/{@code senderAddress}는 값이 없으면 이메일을 아예 보낼 수 없어 {@code @NotBlank}로
 * 기동 시점에 즉시 실패(fail-fast)한다(DataGoKrProperties/JwtProperties와 동일 원칙). {@code accessKey}/
 * {@code secretKey}는 KakaoProperties처럼 검증하지 않는다 — 값이 비어 있으면
 * {@link com.jiseong.homesense.common.mail.SesMailSender}가 AWS SDK 기본 자격 증명 체인(EC2/ECS
 * 인스턴스 역할 등)으로 폴백하도록 SesClientConfig가 분기하므로, accessKey/secretKey 자체가 필수는
 * 아니다.
 */
@ConfigurationProperties(prefix = "homesense.external.aws.ses")
@Validated
public record AwsSesProperties(@NotBlank String region, @NotBlank String senderAddress, String accessKey,
        String secretKey) {
}
