package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.auth.dto.LoginResponse;
import com.jiseong.homesense.auth.dto.ReactivateCommand;
import com.jiseong.homesense.auth.exception.AccountNotActiveException;
import com.jiseong.homesense.auth.exception.AccountNotWithdrawnException;
import com.jiseong.homesense.auth.exception.ReactivationPeriodExpiredException;
import com.jiseong.homesense.common.exception.InvalidCredentialsException;
import com.jiseong.homesense.user.service.WithdrawalTestSeed;
import com.jiseong.homesense.user.service.WithdrawnUserPurgeService;

/**
 * SVC-AUTH-01.reactivate()를 실제 MariaDB 위에서 끝까지 태운다. Mockito 단위 테스트(AuthServiceTest)는 "조건부
 * UPDATE를 올바른 인자로 호출했다"까지만 증명한다 — 여기서는 (1) 벌크 UPDATE가 실제로 status/withdrawn_at/
 * updated_at을 바꾸고, (2) 그 뒤 영속성 컨텍스트가 비워진 상태에서 갱신된 행을 다시 읽어 토큰이 발급되며,
 * (3) 탈퇴 시 폐기된 refresh_token은 폐기 상태 그대로 남고 새 토큰이 추가되고, (4) 관심 매물·알림 설정 같은
 * 소유 데이터가 그대로 복구되는지를 커밋된 DB 상태로 확인한다.
 *
 * <p>{@link LoginAttemptService}(Redis)는 목으로 대체해 이 IT가 Redis 없이도 자기완결적으로 돌게 한다 —
 * package-private 클래스라 이 테스트가 같은 패키지에 있다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class AuthServiceReactivateMariaDbIT {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 5, 0, 0);
    private static final String PASSWORD = "Abcd1234!";

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
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/withdrawn-user-purge-schema.sql");
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        }
    }

    @MockitoBean
    private LoginAttemptService loginAttemptService;

    @Autowired
    private AuthService authService;
    @Autowired
    private WithdrawnUserPurgeService purgeService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    private WithdrawalTestSeed seed;
    private String encodedPassword;

    @BeforeEach
    void setUp() {
        seed = new WithdrawalTestSeed(jdbc);
        seed.wipeAll();
        encodedPassword = passwordEncoder.encode(PASSWORD);
    }

    @Test
    void 유예기간_안이면_복구되고_새_토큰이_발급되며_폐기된_기존_토큰과_소유_데이터는_그대로다() {
        seed.legalDistrict("1111010100");
        long complexId = seed.complex("C-1");
        long tradeId = seed.trade(complexId, "1111010100", "c".repeat(64));
        long userId = seed.user("grace@test.com", encodedPassword, "WITHDRAWN", NOW.minusDays(3));
        seed.seedAllChildren(userId, complexId, "1111010100", tradeId, "old-token"); // 아래에서 폐기 상태로 만든다
        jdbc.update("UPDATE refresh_token SET revoked_yn = TRUE WHERE user_id = ?", userId); // 탈퇴 시 폐기됨
        Map<String, Long> before = seed.childCounts(userId);

        LoginResponse response = authService.reactivate(new ReactivateCommand("Grace@Test.com", PASSWORD));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        // 상태 복구 + updated_at 직접 갱신(벌크 UPDATE는 auditing을 우회한다) + withdrawn_at 비움
        Map<String, Object> row = jdbc.queryForMap("SELECT status, withdrawn_at, updated_at FROM `user` WHERE user_id = ?", userId);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("withdrawn_at")).isNull();
        assertThat(((java.sql.Timestamp) row.get("updated_at")).toLocalDateTime()).isEqualTo(NOW);
        // 폐기된 기존 refresh_token은 폐기 상태 그대로, 새 토큰이 하나 추가됐다.
        assertThat(seed.count("SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_yn = TRUE", userId)).isEqualTo(1);
        assertThat(seed.count("SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_yn = FALSE", userId)).isEqualTo(1);
        // 관심 매물/지역·알림 설정·조회 이력·알림은 그대로(refresh_token만 새 토큰 1건 증가).
        Map<String, Long> after = seed.childCounts(userId);
        assertThat(after.get("refresh_token")).isEqualTo(before.get("refresh_token") + 1);
        after.remove("refresh_token");
        before.remove("refresh_token");
        assertThat(after).isEqualTo(before);
    }

    @Test
    void 정확히_N일이_지난_계정은_철회할_수_없고_행은_그대로_남는다() {
        long userId = seed.user("boundary@test.com", encodedPassword, "WITHDRAWN", NOW.minusDays(7));

        assertThatThrownBy(() -> authService.reactivate(new ReactivateCommand("boundary@test.com", PASSWORD)))
                .isInstanceOf(ReactivationPeriodExpiredException.class);

        assertThat(seed.count("SELECT COUNT(*) FROM `user` WHERE user_id = ? AND status = 'WITHDRAWN'", userId)).isEqualTo(1);
        assertThat(seed.count("SELECT COUNT(*) FROM refresh_token WHERE user_id = ?", userId)).isZero();
    }

    @Test
    void 자동_파기가_먼저_끝난_계정은_자격_증명_단계에서_미존재로_거부된다() {
        seed.user("gone@test.com", encodedPassword, "WITHDRAWN", NOW.minusDays(8));
        purgeService.purgeOne(seed.count("SELECT user_id FROM `user` WHERE email = 'gone@test.com'"), NOW.minusDays(7));

        assertThatThrownBy(() -> authService.reactivate(new ReactivateCommand("gone@test.com", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void 비밀번호가_틀리면_상태와_무관하게_InvalidCredentials이고_행은_변하지_않는다() {
        long userId = seed.user("grace@test.com", encodedPassword, "WITHDRAWN", NOW.minusDays(1));

        assertThatThrownBy(() -> authService.reactivate(new ReactivateCommand("grace@test.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(seed.count("SELECT COUNT(*) FROM `user` WHERE user_id = ? AND status = 'WITHDRAWN'", userId)).isEqualTo(1);
    }

    @Test
    void 정지_계정은_철회할_수_없다() {
        long userId = seed.user("suspended@test.com", encodedPassword, "SUSPENDED", null);

        assertThatThrownBy(() -> authService.reactivate(new ReactivateCommand("suspended@test.com", PASSWORD)))
                .isInstanceOfSatisfying(AccountNotActiveException.class,
                        e -> assertThat(e.errorCode()).isEqualTo("ACCOUNT_SUSPENDED"));

        assertThat(seed.count("SELECT COUNT(*) FROM `user` WHERE user_id = ? AND status = 'SUSPENDED'", userId)).isEqualTo(1);
    }

    @Test
    void 이미_ACTIVE인_계정은_409_대상이다() {
        seed.user("active@test.com", encodedPassword, "ACTIVE", null);

        assertThatThrownBy(() -> authService.reactivate(new ReactivateCommand("active@test.com", PASSWORD)))
                .isInstanceOf(AccountNotWithdrawnException.class);
    }

    @Test
    void 탈퇴한_계정은_비밀번호가_맞아도_login은_ACCOUNT_WITHDRAWN으로_거부한다() {
        seed.user("withdrawn@test.com", encodedPassword, "WITHDRAWN", NOW.minusDays(1));

        assertThatThrownBy(() -> authService.login(new com.jiseong.homesense.auth.dto.LoginCommand("withdrawn@test.com", PASSWORD)))
                .isInstanceOfSatisfying(AccountNotActiveException.class,
                        e -> assertThat(e.errorCode()).isEqualTo("ACCOUNT_WITHDRAWN"));
    }
}
