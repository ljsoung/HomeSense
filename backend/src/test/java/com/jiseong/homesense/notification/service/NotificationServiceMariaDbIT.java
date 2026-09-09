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
 * NotificationService.updateSettings()의 최초 구현은 "조회 → 있으면 UPDATE, 없으면 INSERT"를
 * 애플리케이션에서 분기하고, 동시 INSERT 경쟁으로 인한 UNIQUE 위반은 별도 REQUIRES_NEW 트랜잭션으로
 * 격리해 처리했다. Mockito 단위 테스트는 "그 catch 블록이 재조회 후 갱신을 시도한다"까지만 증명했는데,
 * 실제로는 REQUIRES_NEW로 그 INSERT 실패가 바깥 트랜잭션을 rollback-only로 만드는 문제를 피하더라도,
 * MariaDB 기본 격리수준(REPEATABLE READ)에서는 그 실패 이후 같은(바깥) 트랜잭션에서의 재조회가 이미
 * 확립된 스냅샷에 묶여 경쟁에서 이긴 다른 트랜잭션의 커밋을 여전히 보지 못했다 — 재조회가 다시 empty를
 * 반환해 재시도가 실패하고 예외가 그대로 전파됐다(Codex 코드리뷰 P1 지적). 이 클래스는 원래 그 순서를
 * 두 스레드로 강제 재현해 버그를 드러내려 했던 테스트였다.
 *
 * <p>수정 후 구현은 그 조회·재시도 자체를 없애고 {@link NotificationSettingRepository#upsert}(네이티브
 * {@code INSERT ... ON DUPLICATE KEY UPDATE}) 단일 원자적 문장으로 바꿨다 — 이 방식은 스냅샷 격리
 * 수준과 무관하게 DB가 직접 처리하므로, 특정 커밋 순서를 인위적으로 강제할 필요 없이 "두 요청이 그냥
 * 동시에 들어와도 항상 안전한가"만 확인하면 충분하다. 그래서 이 테스트는 (수정 전과 달리) 트랜잭션을
 * 수동으로 붙잡아 순서를 강제하지 않고, 두 스레드가 동시에 실제 프로덕션 경로(updateSettings())를
 * 호출하게 한 뒤 (1) 둘 다 예외 없이 반환되는지, (2) 최종적으로 정확히 한 행만 남는지만 검증한다.
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

        UpdateNotificationSettingsCommand cmdA =
                new UpdateNotificationSettingsCommand(favoritePropertyId, null, new BigDecimal("3.0"), false, false);
        UpdateNotificationSettingsCommand cmdB =
                new UpdateNotificationSettingsCommand(favoritePropertyId, null, new BigDecimal("7.5"), true, true);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> futureA = executor.submit(() -> {
                awaitUninterruptibly(startLatch);
                notificationService.updateSettings(userId, cmdA);
            });
            Future<?> futureB = executor.submit(() -> {
                awaitUninterruptibly(startLatch);
                notificationService.updateSettings(userId, cmdB);
            });
            startLatch.countDown();

            // 둘 다 예외 없이 반환돼야 한다 — get()이 ExecutionException을 던지면 그 자체가 실패다.
            futureA.get(10, TimeUnit.SECONDS);
            futureB.get(10, TimeUnit.SECONDS);

            List<NotificationSetting> rows = notificationSettingRepository.findByUser_UserId(userId);
            assertThat(rows).hasSize(1);
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

        UpdateNotificationSettingsCommand cmdA =
                new UpdateNotificationSettingsCommand(null, favoriteRegionId, new BigDecimal("3.0"), false, false);
        UpdateNotificationSettingsCommand cmdB =
                new UpdateNotificationSettingsCommand(null, favoriteRegionId, new BigDecimal("7.5"), true, true);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> futureA = executor.submit(() -> {
                awaitUninterruptibly(startLatch);
                notificationService.updateSettings(userId, cmdA);
            });
            Future<?> futureB = executor.submit(() -> {
                awaitUninterruptibly(startLatch);
                notificationService.updateSettings(userId, cmdB);
            });
            startLatch.countDown();

            futureA.get(10, TimeUnit.SECONDS);
            futureB.get(10, TimeUnit.SECONDS);

            List<NotificationSetting> rows = notificationSettingRepository.findByUser_UserId(userId);
            assertThat(rows).hasSize(1);
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
