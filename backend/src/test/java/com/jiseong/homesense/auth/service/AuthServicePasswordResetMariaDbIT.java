package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.auth.entity.RefreshToken;
import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

/**
 * {@code AuthServiceTest.resetPassword_성공하면_...}은 UserRepository/RefreshTokenRepository를 전부
 * Mockito로 목킹해 "user.changePassword()가 Java 필드를 바꿨다"와 "revokeAllByUserId(userId)가 호출됐다"만
 * 증명한다 — {@code UserServiceMariaDbIT}가 {@code withdraw()}에서 이미 검증한 것과 똑같은 함정이
 * 구조적으로 여기도 그대로 있다: {@code AuthService.resetPassword()}도 {@code user.changePassword()}
 * (dirty) 직후 곧바로 {@code refreshTokenRepository.revokeAllByUserId()}(query space가 refresh_token
 * 뿐인 벌크 UPDATE)를 호출하는 같은 순서다. 그 메서드의 {@code flushAutomatically=true}가 이 순서를
 * 안전하게 만들어 주는 것은 맞지만, 이 보장 자체가 실제 DB에서 성립하는지는 Mockito로 증명할 수 없다.
 *
 * <p>이 IT는 실제 MariaDB(Testcontainers) 위에서 resetPassword() 트랜잭션이 끝난 뒤 커밋된 실제 DB
 * 상태를 재조회해, User.password와 RefreshToken.revokedYn이 둘 다 실제로 반영됐는지, 그리고 재설정
 * 토큰이 1회성으로 소비돼 재사용이 불가능한지 함께 검증한다. 스키마는 UserServiceMariaDbIT와 동일한
 * user/refresh_token 두 테이블만 필요해 그 IT의 fixture(user-withdraw-schema.sql)를 그대로 재사용한다.
 *
 * <p>Redis 연결은 LoginAttemptService/UserStatusCacheService가 이미 요구하던 인프라라 이 IT로 새로
 * 추가되는 테스트 인프라 요구사항은 없다(CLAUDE.md COM-SEC-01/02 절 참고) — {@link PasswordResetTokenService}도
 * 같은 로컬 Redis(localhost:6379)를 그대로 쓴다.
 *
 * <p>Docker가 필요해 기본 `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class AuthServicePasswordResetMariaDbIT {

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
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/user-withdraw-schema.sql");
    }

    @Autowired
    private AuthService authService;
    @Autowired
    private PasswordResetTokenService passwordResetTokenService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void 재설정하면_커밋_이후_재조회에서도_새_비밀번호와_토큰_전체_폐기가_모두_반영돼_있고_토큰은_1회성이다() {
        User user = userRepository.saveAndFlush(
                User.createUser("reset-it@test.com", passwordEncoder.encode("OldAbcd1234!"), "닉네임"));
        RefreshToken token = refreshTokenRepository.saveAndFlush(
                RefreshToken.issue(user, "token-hash", LocalDateTime.now().plusDays(1)));
        String rawToken = passwordResetTokenService.issueToken(user.getUserId());

        authService.resetPassword(rawToken, "NewAbcd1234!");

        // authService.resetPassword()가 반환된 시점엔 이미 트랜잭션이 커밋된 뒤다(프록시 경계) —
        // 여기서 다시 조회하는 User/RefreshToken은 resetPassword() 호출 중 쓰던 것과 같은 영속성
        // 컨텍스트가 아니라 새로 읽어온 값이라, 실제 커밋된 DB 상태만 확인한다.
        User reloadedUser = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewAbcd1234!", reloadedUser.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("OldAbcd1234!", reloadedUser.getPassword())).isFalse();

        RefreshToken reloadedToken = refreshTokenRepository.findById(token.getRefreshTokenId()).orElseThrow();
        assertThat(reloadedToken.isUsable()).isFalse();

        // 토큰은 GETDEL로 이미 소비됐다 — 같은 토큰으로 다시 consume을 시도하면 빈 Optional이어야 한다.
        assertThat(passwordResetTokenService.consumeToken(rawToken)).isEmpty();
    }
}
