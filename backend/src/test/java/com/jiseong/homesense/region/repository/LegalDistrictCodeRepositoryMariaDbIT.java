package com.jiseong.homesense.region.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.region.entity.LegalDistrictCode;

/**
 * SVC-RGN-01.autocomplete() 전용 검증 — {@code searchByNameContaining()}이 시도/시군구 대표행
 * (계층 상위 행, eupmyeondongName=null)을 제외하고 선택 가능한 읍면동(leaf) 행만 반환하는지는
 * Mockito로 증명할 수 없다({@code RegionServiceTest}는 리포지토리를 목킹해 이 WHERE 절 자체를
 * 우회한다). 대표행이 후보에 섞이면 그 legal_dong_cd는 {@code LegalDistrictMatcher.matchByTradeSggCd()}가
 * 절대 거래에 매칭시키지 않는 코드라(읍면동명이 umdNm과 일치하는 행에만 매칭), 자동완성에서 그 행을
 * 고르면 실거래 검색·관심지역 통계가 항상 0건으로 막힌다(Codex 코드리뷰 P1 — CLAUDE.md SVC-RGN-01
 * 절 참고).
 *
 * <p>Docker가 필요해 기본 {@code ./gradlew test}에서는 제외되고 {@code ./gradlew integrationTest}로만
 * 실행된다. {@code @Test} 메서드가 둘 이상이고 {@code @BeforeEach}가 PK(legal_dong_cd)를 고정값으로
 * 심으므로 클래스에 {@code @Transactional}을 건다(ComplexRepositoryMariaDbIT가 문서화한 함정,
 * CLAUDE.md 참고).
 */
@SpringBootTest
@Testcontainers
@Transactional
@Tag("integration")
class LegalDistrictCodeRepositoryMariaDbIT {

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:10.11")
            .withDatabaseName("homesense_it")
            .withUsername("homesense")
            .withPassword("homesense");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", MARIADB::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations",
                () -> "classpath:testcontainers/legal-district-search-schema.sql");
    }

    @Autowired
    private LegalDistrictCodeRepository legalDistrictCodeRepository;

    @BeforeEach
    void setUp() {
        legalDistrictCodeRepository.saveAndFlush(hierarchyRow("1168000000", "서울특별시", "강남구"));
        legalDistrictCodeRepository.saveAndFlush(leafRow("1168010100", "서울특별시", "강남구", "역삼동"));
        legalDistrictCodeRepository.saveAndFlush(leafRow("1168010600", "서울특별시", "강남구", "삼성동"));
        legalDistrictCodeRepository.saveAndFlush(inactiveLeafRow("1168011000", "서울특별시", "강남구", "폐지동"));
    }

    private static LegalDistrictCode hierarchyRow(String cd, String sido, String sigungu) {
        return LegalDistrictCode.builder()
                .legalDongCd(cd)
                .legalDongName(sido + " " + sigungu)
                .sidoName(sido)
                .sigunguName(sigungu)
                .eupmyeondongName(null)
                .isActive(true)
                .dataVersion(LocalDate.of(2026, 1, 1))
                .build();
    }

    private static LegalDistrictCode leafRow(String cd, String sido, String sigungu, String dong) {
        return LegalDistrictCode.builder()
                .legalDongCd(cd)
                .legalDongName(sido + " " + sigungu + " " + dong)
                .sidoName(sido)
                .sigunguName(sigungu)
                .eupmyeondongName(dong)
                .isActive(true)
                .dataVersion(LocalDate.of(2026, 1, 1))
                .build();
    }

    private static LegalDistrictCode inactiveLeafRow(String cd, String sido, String sigungu, String dong) {
        return LegalDistrictCode.builder()
                .legalDongCd(cd)
                .legalDongName(sido + " " + sigungu + " " + dong)
                .sidoName(sido)
                .sigunguName(sigungu)
                .eupmyeondongName(dong)
                .isActive(false)
                .dataVersion(LocalDate.of(2026, 1, 1))
                .build();
    }

    @Test
    void 시군구_대표행은_제외되고_활성_읍면동_행만_후보로_반환된다() {
        List<LegalDistrictCode> results = legalDistrictCodeRepository
                .searchByNameContaining("강남", PageRequest.of(0, 10));

        assertThat(results).extracting(LegalDistrictCode::getLegalDongCd)
                .containsExactlyInAnyOrder("1168010100", "1168010600")
                .doesNotContain("1168000000", "1168011000");
    }
}
