package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * COM-CFG-01. homesense.external.data-go-kr.* 설정을 바인딩한다.
 * 실제 값은 DATA_GO_KR_SERVICE_KEY 환경변수(.env)에서 오며 코드에는 절대 하드코딩하지 않는다.
 */
@ConfigurationProperties(prefix = "homesense.external.data-go-kr")
public record DataGoKrProperties(String serviceKey) {
}
