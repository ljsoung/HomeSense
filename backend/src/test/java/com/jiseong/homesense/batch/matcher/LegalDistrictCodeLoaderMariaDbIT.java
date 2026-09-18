package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.Trade;
import com.jiseong.homesense.trade.repository.TradeRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 2026년 법정동코드 참조자료 노후화 대응(화성시 일반구 신설, 인천 행정체제 개편, 전남광주통합특별시
 * 출범) 재적재 작업 전용 안전성 검증 — {@code LegalDistrictCodeLoaderTest}(Mockito)는
 * {@code saveAll()}이 어떤 인자로 호출되는지만 증명하고, "실제 FK를 가진 DB 위에서 재적재해도 기존
 * trade 레코드가 참조하는 행이 삭제되지 않는지"는 증명하지 못한다 — 이 프로젝트가 이미 여러 번 겪은
 * "UNIQUE 제약/데이터 변형 로직은 Mockito로 증명 불가" 원칙(CLAUDE.md 참고)과 같은 이유다.
 *
 * <p>{@code LegalDistrictCodeLoader.loadInitial()}은 {@code deactivateAll()}(전체 비활성화) →
 * {@code saveAll(rows)}(이번 CSV의 "존재" 행만 upsert로 재활성화) 패턴이라 DELETE를 전혀 쓰지 않는다 —
 * 이 테스트는 그 설계가 실제로 안전한지(폐지된 코드가 DB에서 사라지지 않고, 그 코드를 참조하는 trade
 * FK가 깨지지 않는지)를 1라운드(구버전 CSV) → 2라운드(신버전 CSV, 일부 코드가 폐지되고 일부는 신설)
 * 재적재로 재현해 검증한다.
 *
 * <p>Docker가 필요해 기본 {@code ./gradlew test}에서는 제외되고 {@code ./gradlew integrationTest}로만
 * 실행된다. {@code @Test} 메서드가 둘 이상이고 {@code @BeforeEach} 없이 각 테스트가 스스로 CSV를
 * 만들어 적재하므로 fixture 충돌이 없지만(모든 legal_dong_cd가 테스트마다 다른 스키마 인스턴스가
 * 아니라 같은 컨테이너를 공유하므로), 안전하게 클래스에 {@code @Transactional}을 걸어 각 테스트 종료 후
 * 자동 롤백되게 한다.
 *
 * <p><b>라운드 사이에 {@code entityManager.clear()}가 반드시 필요하다.</b> 실제 운영에서는
 * {@code loadInitial()} 각 호출이 별도 요청(별도 영속성 컨텍스트)으로 실행되지만, 이 테스트는 클래스
 * 레벨 {@code @Transactional}로 한 테스트 메서드 전체가 하나의 영속성 컨텍스트를 공유한다 —
 * {@code deactivateAll()}은 벌크 UPDATE라 DB는 즉시 바뀌어도 이미 그 PK로 로드돼 있던 managed 엔티티의
 * 인메모리 상태는 갱신하지 않는다(CLAUDE.md "@Modifying 벌크 쿼리" 원칙과 같은 함정의 변형). 1라운드
 * 이후 clear 없이 바로 {@code findById()}를 또 부르면 1차 캐시(영속성 컨텍스트) 히트로 그 stale
 * 인스턴스를 그대로 돌려줘 "재적재해도 값이 안 바뀐 것처럼" 보이는 거짓 양성이 생긴다 — 실제 버그가
 * 아니라 순수 테스트 방법론 문제라, 코드가 아니라 테스트 쪽에서 라운드 경계마다 명시적으로 clear한다.
 */
@SpringBootTest
@Testcontainers
@Transactional
@Tag("integration")
class LegalDistrictCodeLoaderMariaDbIT {

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
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/trade-race-schema.sql");
    }

    @Autowired
    private LegalDistrictCodeLoader loader;
    @Autowired
    private LegalDistrictCodeRepository legalDistrictCodeRepository;
    @Autowired
    private TradeRepository tradeRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @TempDir
    Path tempDir;

    /**
     * 1라운드(구버전, 화성시 통합코드 4159000000만 존재) → trade가 그 코드를 참조 → 2라운드(신버전,
     * 4159000000은 그대로 두고 4159100000(만세구) 신설, 인천 중구 2811000000은 폐지) 재적재 후:
     * 폐지된 2811000000이 삭제되지 않고 is_active=false로 남아있고, 그 코드를 참조하던 trade의
     * legal_dong_cd FK가 여전히 유효한지(SET NULL로 끊기지 않았는지) 검증한다.
     */
    @Test
    void 재적재로_폐지된_법정동코드는_삭제되지_않고_비활성화만_되며_참조하는_trade의_FK가_유지된다() throws IOException {
        loader.loadInitial(writeCsv("""
                법정동코드	법정동명	폐지여부
                4100000000	경기도	존재
                4159000000	경기도 화성시	존재
                2800000000	인천광역시	존재
                2811000000	인천광역시 중구	존재
                """));

        LegalDistrictCode incheonJunggu = legalDistrictCodeRepository.findById("2811000000").orElseThrow();
        Trade tradeInDeprecatedCode = tradeRepository.saveAndFlush(Trade.builder()
                .housingType(HousingType.APT).dealCategory(DealCategory.SALE)
                .datasetId("15126469").sggCd("28110").legalDistrictCode(incheonJunggu).umdNm("신포동")
                .excluUseArea(new BigDecimal("59.99"))
                .dealDate(LocalDate.of(2024, 3, 1)).dealAmount(50000L)
                .cancelYn(false).dedupHash("dedup-deprecated-1")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());

        // 클래스 javadoc 참고 — FK 저장은 이미 DB에 반영됐으므로 clear 이후에도 안전하다.
        entityManager.clear();

        loader.loadInitial(writeCsv("""
                법정동코드	법정동명	폐지여부
                4100000000	경기도	존재
                4159000000	경기도 화성시	존재
                4159100000	경기도 화성시 만세구	존재
                2800000000	인천광역시	존재
                2811000000	인천광역시 중구	폐지
                """));

        Optional<LegalDistrictCode> deprecated = legalDistrictCodeRepository.findById("2811000000");
        assertThat(deprecated).isPresent();
        assertThat(deprecated.get().isActive()).isFalse();

        Optional<LegalDistrictCode> newlyAdded = legalDistrictCodeRepository.findById("4159100000");
        assertThat(newlyAdded).isPresent();
        assertThat(newlyAdded.get().isActive()).isTrue();
        assertThat(newlyAdded.get().getSigunguName()).isEqualTo("화성시 만세구");

        Optional<Trade> survivor = tradeRepository.findById(tradeInDeprecatedCode.getTradeId());
        assertThat(survivor).isPresent();
        assertThat(survivor.get().getLegalDistrictCode()).isNotNull();
        assertThat(survivor.get().getLegalDistrictCode().getLegalDongCd()).isEqualTo("2811000000");
    }

    /**
     * 전남광주통합특별시 출범 시나리오(광주광역시/전라남도 폐지 → 통합 시도 코드 신설)를 별도로
     * 재현한다. 1라운드에서 광주광역시(2900000000)가 활성 상태로 먼저 존재해야, 2라운드의 폐지 이후
     * "삭제되지 않고 비활성화만 되는지"를 검증할 대상이 생긴다 — 애초에 한 번도 적재된 적 없는
     * 코드는 존재 자체가 없어 이 시나리오를 재현하지 못한다.
     */
    @Test
    void 재적재_후_활성_시군구코드_개수가_새_CSV_기준으로_정확히_갱신된다() throws IOException {
        loader.loadInitial(writeCsv("""
                법정동코드	법정동명	폐지여부
                2900000000	광주광역시	존재
                2911000000	광주광역시 동구	존재
                """));

        entityManager.clear();

        loader.loadInitial(writeCsv("""
                법정동코드	법정동명	폐지여부
                1200000000	전남광주통합특별시	존재
                1211000000	전남광주통합특별시 목포시	존재
                2900000000	광주광역시	폐지
                2911000000	광주광역시 동구	폐지
                """));

        List<String> activeSggCds = legalDistrictCodeRepository.findDistinctActiveSggCd();
        assertThat(activeSggCds).containsExactlyInAnyOrder("12000", "12110");

        Optional<LegalDistrictCode> deprecatedGwangju = legalDistrictCodeRepository.findById("2900000000");
        assertThat(deprecatedGwangju).isPresent();
        assertThat(deprecatedGwangju.get().isActive()).isFalse();

        Optional<LegalDistrictCode> deprecatedGwangjuGu = legalDistrictCodeRepository.findById("2911000000");
        assertThat(deprecatedGwangjuGu).isPresent();
        assertThat(deprecatedGwangjuGu.get().isActive()).isFalse();
    }

    private File writeCsv(String content) throws IOException {
        Path csvPath = tempDir.resolve("legal_district_code_" + System.nanoTime() + ".csv");
        Files.write(csvPath, content.getBytes(Charset.forName("MS949")));
        return csvPath.toFile();
    }
}
