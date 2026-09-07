package com.jiseong.homesense.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;

/**
 * SVC-CPX-01 — 이 프로젝트에서 QueryDSL을 실제로 쓰는 첫 지점이다. search()/searchInBounds()가 필터
 * 조합(주택유형 다중선택, 지역, 면적/금액/건축년도 범위 등)에 따라 조건절을 동적으로 구성해야 해서,
 * 파생 쿼리 메서드나 고정된 JPQL @Query로는 표현하기 어렵다.
 */
@Configuration
public class QuerydslConfig {

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
