package com.jiseong.homesense;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.jiseong.homesense.support.RedisContainerSupport;

/**
 * 새로 설치하는 DB의 유일한 기준인 저장소 루트 {@code schema_all.sql}이 현재 엔티티와 맞는지 확인한다.
 *
 * <p>빈 MariaDB 10.11 컨테이너에 이 파일만 적용한 뒤(이미지 init 스크립트 — 운영에서 {@code mysql < schema_all.sql}로
 * 적용하는 것과 같다) {@code ddl-auto=validate}로 컨텍스트를 띄운다. 다른 IT는 테이블별 fixture
 * ({@code testcontainers/*.sql})로 스키마를 만들므로 이 파일을 검증하지 않는다. 엔티티에 컬럼을 더하고 이 파일을 고치지 않으면
 * 여기서 기동이 실패한다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class SchemaAllValidateMariaDbIT extends RedisContainerSupport {

    /** Gradle 테스트의 작업 디렉터리는 backend/라 저장소 루트는 한 단계 위다. */
    private static final Path SCHEMA_ALL = Path.of("..", "schema_all.sql").toAbsolutePath().normalize();

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:10.11")
            .withDatabaseName("homesense")
            .withUsername("homesense")
            .withPassword("homesense")
            .withCopyFileToContainer(MountableFile.forHostPath(SCHEMA_ALL), "/docker-entrypoint-initdb.d/schema_all.sql");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", MARIADB::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void schema_all만_적용한_빈_DB에서_ddl_auto_validate로_기동된다() {
        assertThat(Files.exists(SCHEMA_ALL)).as("schema_all.sql 경로: %s", SCHEMA_ALL).isTrue();

        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'homesense' ORDER BY table_name",
                String.class);
        assertThat(tables).containsExactlyInAnyOrder("batch_log", "complex", "favorite_property", "favorite_region",
                "legal_district_code", "notification", "notification_setting", "recent_view", "refresh_token",
                "search_log", "trade", "user");

        // 개별 마이그레이션 파일(schema/*.sql)의 내용이 이 파일에도 들어 있는지 — validate는 길이·NULL 여부를 보지 않는다.
        assertThat(columnType("notification", "sent_at")).isEqualTo("datetime/YES");
        assertThat(columnType("complex", "location_precision")).isEqualTo("varchar(12)/YES");
        assertThat(columnType("trade", "location_precision")).isEqualTo("varchar(12)/YES");
        Integer indexes = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                WHERE table_schema = 'homesense' AND index_name IN (
                  'idx_trade_complex_deal_date', 'idx_trade_legal_dong_deal_date', 'idx_trade_housing_deal_category',
                  'idx_trade_deal_date', 'idx_complex_name', 'idx_complex_region', 'idx_complex_location',
                  'idx_legal_district_region_name', 'idx_recent_view_user_viewed', 'idx_recent_view_session_viewed',
                  'idx_notification_user_read_sent', 'idx_batch_log_started_at', 'idx_batch_log_success_started',
                  'idx_search_log_keyword_searched_at')""", Integer.class);
        assertThat(indexes).isEqualTo(14);
    }

    private String columnType(String table, String column) {
        return jdbc.queryForObject("""
                SELECT CONCAT(column_type, '/', is_nullable) FROM information_schema.columns
                WHERE table_schema = 'homesense' AND table_name = ? AND column_name = ?""", String.class, table, column);
    }
}
