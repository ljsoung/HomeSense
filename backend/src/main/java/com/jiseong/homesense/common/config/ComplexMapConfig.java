package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link ComplexMapProperties}를 등록한다(API-CPX-01 지도 범위 조회). */
@Configuration
@EnableConfigurationProperties(ComplexMapProperties.class)
public class ComplexMapConfig {
}
