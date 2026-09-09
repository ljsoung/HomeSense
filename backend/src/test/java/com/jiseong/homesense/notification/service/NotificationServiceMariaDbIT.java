package com.jiseong.homesense.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.favorite.entity.FavoriteProperty;
import com.jiseong.homesense.favorite.entity.FavoriteRegion;
import com.jiseong.homesense.favorite.repository.FavoritePropertyRepository;
import com.jiseong.homesense.favorite.repository.FavoriteRegionRepository;
import com.jiseong.homesense.notification.dto.UpdateNotificationSettingsCommand;
import com.jiseong.homesense.notification.entity.NotificationSetting;
import com.jiseong.homesense.notification.repository.NotificationSettingRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

/**
 * NotificationServiceTest의 "INSERT시점에_UNIQUE_위반이_발생하면..." 테스트는 {@code
 * NotificationSettingInsertGateway.insert()}가 DataIntegrityViolationException을 던지도록 목킹한
 * 순수 단위 테스트라 "NotificationService의 catch 블록이 재조회 후 갱신을 시도한다"만 증명하고,
 * 그 재조회·갱신이 실제 MariaDB 위에서 (rollback-only로 표시되지 않은) 유효한 트랜잭션 안에서
 * 정말로 커밋되는지는 증명하지 못한다 — FavoriteServiceMariaDbIT/TradeChunkLoaderMariaDbIT와 같은
 * 이유(CLAUDE.md "UNIQUE 제약 동시성 회귀 테스트 원칙" 참고)로, 실제 MariaDB(Testcontainers) 위에서
 * NotificationService.updateSettings()가 실제로 쓰는 @Transactional 경계 안에서, 두 스레드가 실제로
 * 같은 대상을 두고 경쟁하게 만들어 검증한다.
 *
 * <p>FAV의 addFavorite*()와 달리 이 API는 "등록 거부"가 아니라 "upsert"라, 경쟁에서 진 쪽도 예외 없이
 * 정상 반환되어야 하고 최종 저장된 값은 진 쪽이 요청한 조건이어야 한다 — 이 클래스는 그 두 조건을
 * 모두 검증한다. 각 테스트가 서로 다른 user/favoriteProperty/favoriteRegion 값을 써서 독립적이므로
 * 클래스 레벨 @Transactional이 필요 없다(동시성 IT는 그 원칙의 예외 대상, CLAUDE.md 참고).
 *
 * <p>Docker가 필요해 기본 `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 * 이 리포지토리 환경에서 Docker 데몬을 쓸 수 없어 작성 시점에 실제 실행까지는 확인하지 못했다 —
 * `./gradlew integrationTest`로 반드시 재확인하라.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class NotificationServiceMariaDbIT {

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
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/notification-race-schema.sql");
    }

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationSettingRepository notificationSettingRepository;
    @Autowired
    private FavoritePropertyRepository favoritePropertyRepository;
    @Autowired
    private FavoriteRegionRepository favoriteRegionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ComplexRepository complexRepository;
    @Autowired
    private LegalDistrictCodeRepository legalDistrictCodeRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private static Complex complex(String sourceComplexCd) {
        return Complex.builder()
                .sourceComplexCd(sourceComplexCd)
                .complexName("경쟁단지")
                .complexType("아파트")
                .elevatorPassengerCount((short) 1)
                .elevatorCargoCount((short) 0)
                .elevatorCombinedCount((short) 0)
                .dataUpdatedAt(LocalDate.of(2026, 1, 1))
                .build();
    }

    private static LegalDistrictCode region(String legalDongCd) {
        return LegalDistrictCode.builder()
                .legalDongCd(legalDongCd)
                .legalDongName("서울특별시 강남구 역삼동")
                .sidoName("서울특별시")
                .sigunguName("강남구")
                .eupmyeondongName("역삼동")
                .isActive(true)
                .dataVersion(LocalDate.of(2026, 1, 1))
                .build();
    }

    @Test
    void 같은_관심매물에_동시에_알림설정을_저장하면_둘_다_예외없이_반환되고_한_행만_남는다() throws Exception {
        User user = userRepository.saveAndFlush(
                User.createUser("ntf-race-property@test.com", "encoded", "경쟁회원"));
        Complex complex = complexRepository.saveAndFlush(complex("SRC-NTF-RACE-1"));
        FavoriteProperty favorite = favoritePropertyRepository.saveAndFlush(
                FavoriteProperty.register(user, complex, HousingType.APT));
        Long userId = user.getUserId();
        Long favoritePropertyId = favorite.getFavoritePropertyId();

        CountDownLatch insertedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Thread A: 다른 동시 updateSettings() 호출이 먼저 INSERT를 끝낸 상황을 재현한다 —
            // INSERT까지만 실행하고 releaseLatch가 열릴 때까지 커밋하지 않고 트랜잭션을 붙잡아 둔다.
            Future<?> holderFuture = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                assertThat(notificationSettingRepository
                        .findByUser_UserIdAndFavoriteProperty_FavoritePropertyId(userId, favoritePropertyId))
                        .isEmpty();
                User userRef = userRepository.getReferenceById(userId);
                FavoriteProperty favoriteRef = favoritePropertyRepository.getReferenceById(favoritePropertyId);
                notificationSettingRepository.saveAndFlush(NotificationSetting.forProperty(
                        userRef, favoriteRef, new BigDecimal("3.0"), false, false));
                insertedLatch.countDown();
                awaitUninterruptibly(releaseLatch);
            }));

            // A가 INSERT까지는 마쳤지만 아직 커밋 전이라는 걸 확인한 뒤, 같은 대상으로 실제 프로덕션
            // 경로(NotificationService.updateSettings())를 호출한다. B의 findByXxx()는 A의 미확정
            // INSERT를 보지 못해 empty를 받고, NotificationSettingInsertGateway.insert()를 시도하다
            // A가 쥔 미확정 UNIQUE 인덱스 항목에 걸려 블록된다.
            assertThat(insertedLatch.await(10, TimeUnit.SECONDS)).isTrue();
            UpdateNotificationSettingsCommand loserCmd =
                    new UpdateNotificationSettingsCommand(favoritePropertyId, null, new BigDecimal("7.5"), true, true);
            Future<?> loserFuture = executor.submit(() -> notificationService.updateSettings(userId, loserCmd));

            // B가 findByXxx()를 지나 블로킹 INSERT에 도달할 시간을 준 뒤 A를 풀어 커밋시킨다 — A가
            // 커밋되는 순간 B의 블록된 INSERT가 재개되며 실제 UNIQUE 위반으로 실패하고,
            // NotificationSettingInsertGateway가 REQUIRES_NEW로 격리해 둔 덕에 updateSettings()의
            // 트랜잭션은 오염되지 않아 catch 블록의 재조회·갱신이 그대로 커밋된다.
            Thread.sleep(500);
            releaseLatch.countDown();
            holderFuture.get(10, TimeUnit.SECONDS);
            loserFuture.get(10, TimeUnit.SECONDS);

            List<NotificationSetting> rows = notificationSettingRepository.findByUser_UserId(userId);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getPriceChangeThresholdPct()).isEqualByComparingTo("7.5");
            assertThat(rows.get(0).isNewTradeAlertYn()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 같은_관심지역에_동시에_알림설정을_저장하면_둘_다_예외없이_반환되고_한_행만_남는다() throws Exception {
        User user = userRepository.saveAndFlush(
                User.createUser("ntf-race-region@test.com", "encoded", "경쟁회원2"));
        LegalDistrictCode region = legalDistrictCodeRepository.saveAndFlush(region("1168099998"));
        FavoriteRegion favorite = favoriteRegionRepository.saveAndFlush(FavoriteRegion.register(user, region));
        Long userId = user.getUserId();
        Long favoriteRegionId = favorite.getFavoriteRegionId();

        CountDownLatch insertedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holderFuture = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                assertThat(notificationSettingRepository
                        .findByUser_UserIdAndFavoriteRegion_FavoriteRegionId(userId, favoriteRegionId))
                        .isEmpty();
                User userRef = userRepository.getReferenceById(userId);
                FavoriteRegion favoriteRef = favoriteRegionRepository.getReferenceById(favoriteRegionId);
                notificationSettingRepository.saveAndFlush(NotificationSetting.forRegion(
                        userRef, favoriteRef, new BigDecimal("3.0"), false, false));
                insertedLatch.countDown();
                awaitUninterruptibly(releaseLatch);
            }));

            assertThat(insertedLatch.await(10, TimeUnit.SECONDS)).isTrue();
            UpdateNotificationSettingsCommand loserCmd =
                    new UpdateNotificationSettingsCommand(null, favoriteRegionId, new BigDecimal("7.5"), true, true);
            Future<?> loserFuture = executor.submit(() -> notificationService.updateSettings(userId, loserCmd));

            Thread.sleep(500);
            releaseLatch.countDown();
            holderFuture.get(10, TimeUnit.SECONDS);
            loserFuture.get(10, TimeUnit.SECONDS);

            List<NotificationSetting> rows = notificationSettingRepository.findByUser_UserId(userId);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getPriceChangeThresholdPct()).isEqualByComparingTo("7.5");
            assertThat(rows.get(0).isNewTradeAlertYn()).isTrue();
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
