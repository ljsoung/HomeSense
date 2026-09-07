package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.auth.dto.SignupCommand;
import com.jiseong.homesense.auth.exception.DuplicateEmailException;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

/**
 * AuthServiceTest의 "동시_가입_경쟁" 테스트는 {@code userRepository.save()}를 목킹해
 * {@code DataIntegrityViolationException}을 강제로 던지는 순수 단위 테스트라 "AuthService의 catch 블록이
 * 실행된다"만 증명하고, 그 전제("email UNIQUE 위반이 save() 호출 시점에 곧바로 터진다")까지는 증명하지
 * 못한다. 그 전제는 {@code User.userId}가 {@code GenerationType.IDENTITY}일 때만 성립한다 — SEQUENCE/AUTO처럼
 * Hibernate가 INSERT를 트랜잭션 flush(커밋) 시점까지 미룰 수 있는 전략이었다면, UNIQUE 위반은 signup()이
 * 이미 반환된 뒤 AuthService의 바깥쪽 @Transactional이 커밋을 시도할 때 터져 이 메서드의 try/catch를
 * 완전히 비껴간다(코드리뷰에서 지적됨).
 *
 * <p>이 테스트는 그 전제를 실제 MariaDB(Testcontainers) 위에서, signup()이 실제로 쓰는 것과 동일한
 * @Transactional 경계 안에서 검증한다 — repository.save()를 트랜잭션 바깥에서 단독 호출하면 Spring Data
 * JPA가 그 호출 자체를 위한 짧은 자체 트랜잭션을 새로 열고 커밋까지 마친 뒤 반환하므로, 실제로는 flush가
 * 지연되는 전략이었어도 "save() 호출 시점에 곧바로 예외가 난 것처럼" 보이는 착시가 생긴다(거짓 양성).
 * AuthService.signup()의 진짜 호출 경로(바깥쪽 @Transactional에 join되는 save())를 그대로 타야 이 착시를
 * 피할 수 있어, TradeChunkLoaderMariaDbIT와 같은 방식으로 두 스레드가 실제로 같은 이메일을 두고 경쟁하게
 * 만든다.
 *
 * <p>스키마는 테이블정의서 8장 DDL 원문 중 이 테스트가 실제로 건드리는 user 테이블만 재구성한
 * 것이다(testcontainers/user-race-schema.sql).
 *
 * <p>Docker가 필요해 기본 `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class AuthServiceMariaDbIT {

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
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/user-race-schema.sql");
    }

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 같은_이메일로_두_회원가입_요청이_동시에_경쟁하면_하나만_성공하고_다른_하나는_DuplicateEmailException으로_응답한다()
            throws Exception {
        String email = "race@test.com";
        CountDownLatch insertedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Thread A: existsByEmail(miss) -> INSERT까지만 실행하고, releaseLatch가 열릴 때까지
            // 커밋하지 않고 트랜잭션을 붙잡아 둔다 — "existsByEmail() 조회 이후 이 요청이 INSERT하기
            // 전에 다른 요청이 먼저 INSERT를 끝낸" 상황에서 "먼저 끝낸 쪽"을 실제 트랜잭션으로 재현한다.
            Future<?> holderFuture = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                assertThat(userRepository.existsByEmail(email)).isFalse();
                userRepository.saveAndFlush(User.createUser(email, "encoded-a", "먼저가입"));
                insertedLatch.countDown();
                awaitUninterruptibly(releaseLatch);
            }));

            // A가 INSERT까지는 마쳤지만 아직 커밋 전이라는 걸 확인한 뒤, 같은 이메일로 실제 프로덕션
            // 경로(AuthService.signup())를 호출한다. InnoDB 기본 격리수준(REPEATABLE READ)이라 B의
            // existsByEmail()은 A의 미확정 INSERT를 보지 못해 false를 받고, save()를 시도하다 A가
            // 쥔 미확정 UNIQUE 인덱스 항목에 걸려 블록된다.
            assertThat(insertedLatch.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> loserFuture = executor.submit(() -> assertThatThrownBy(
                    () -> authService.signup(new SignupCommand(email, "Abcd1234!", "나중가입")))
                    .isInstanceOf(DuplicateEmailException.class));

            // B가 existsByEmail을 지나 블로킹 INSERT에 도달할 시간을 준 뒤 A를 풀어 커밋시킨다 — A가
            // 커밋되는 순간 B의 블록된 INSERT가 재개되며 실제 email UNIQUE 위반으로 실패하고,
            // AuthService.signup()의 catch(DataIntegrityViolationException) 경로를 실제로 태운다.
            Thread.sleep(500);
            releaseLatch.countDown();
            holderFuture.get(10, TimeUnit.SECONDS);
            loserFuture.get(10, TimeUnit.SECONDS);

            List<User> rows = userRepository.findAll();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getNickname()).isEqualTo("먼저가입");
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    latch.await();
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
