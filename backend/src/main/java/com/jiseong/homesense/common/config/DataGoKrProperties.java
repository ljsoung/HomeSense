package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * COM-CFG-01. homesense.external.data-go-kr.* 설정을 바인딩한다.
 * 실제 값은 DATA_GO_KR_SERVICE_KEY 환경변수(.env)에서 오며 코드에는 절대 하드코딩하지 않는다(NFR-4).
 *
 * <p>환경변수 자체가 없으면(플레이스홀더에 기본값이 없어) Spring이 프로퍼티 해석 단계에서 이미 기동을
 * 막지만, 환경변수가 빈 문자열로 설정된 경우까지는 잡아내지 못한다 — {@code @NotBlank}가 그 틈을
 * 메워 값이 비어 있으면 애플리케이션 기동 시점에 즉시 실패(fail-fast)하게 한다.
 */
@ConfigurationProperties(prefix = "homesense.external.data-go-kr")
@Validated
public record DataGoKrProperties(@NotBlank String serviceKey) {
}
