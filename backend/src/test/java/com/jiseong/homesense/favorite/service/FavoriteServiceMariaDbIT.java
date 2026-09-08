package com.jiseong.homesense.favorite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.jiseong.homesense.favorite.dto.AddFavoritePropertyCommand;
import com.jiseong.homesense.favorite.dto.AddFavoriteRegionCommand;
import com.jiseong.homesense.favorite.entity.FavoriteProperty;
import com.jiseong.homesense.favorite.entity.FavoriteRegion;
import com.jiseong.homesense.favorite.exception.DuplicateFavoriteException;
import com.jiseong.homesense.favorite.exception.DuplicateFavoriteRegionException;
import com.jiseong.homesense.favorite.repository.FavoritePropertyRepository;
import com.jiseong.homesense.favorite.repository.FavoriteRegionRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

/**
 * FavoriteServiceTest의 "save시점에_UNIQUE_위반이_발생하면..." 두 테스트는 각 Repository.save()가
 * DataIntegrityViolationException을 던지도록 목킹한 순수 단위 테스트라 "FavoriteService의 catch
 * 블록이 실행된다"만 증명하고, 그 전제("UNIQUE 위반이 save() 호출 시점에 곧바로 터진다")까지는
 * 증명하지 못한다. 그 전제는 FavoriteProperty.favoritePropertyId/FavoriteRegion.favoriteRegionId가
 * {@code GenerationType.IDENTITY}일 때만 성립한다 — AuthServiceMariaDbIT/TradeChunkLoaderMariaDbIT와
 * 같은 이유(CLAUDE.md "UNIQUE 제약 동시성 회귀 테스트 원칙" 참고)로, 실제 MariaDB(Testcontainers) 위에서
 * FavoriteService.addFavoriteProperty()/addFavoriteRegion()이 실제로 쓰는 @Transactional 경계 안에서,
 * 두 스레드가 실제로 같은 조합을 두고 경쟁하게 만들어 검증한다.
 *
 * <p>이 클래스는 @Test 메서드가 둘이지만 각 테스트가 서로 다른 user/complex/legal_dong_cd 값을 써서
 * UNIQUE 충돌 없이 독립적이므로 클래스 레벨 @Transactional이 필요 없다 — 오히려 동시성 IT는 그 원칙의
 * 예외 대상이다(메인 스레드의 @Transactional은 워커 스레드에 보이지 않아 검증하려는 시나리오 자체가
 * 깨진다, CLAUDE.md 참고). user/complex/legal_district_code 픽스처는 메인 스레드에서
 * saveAndFlush()로 미리 커밋해 두 워커 스레드 모두에서 곧바로 보이게 한다.
 *
 * <p>Docker가 필요해 기본 `./gradlew test`에서는 제외되고 `./gradlew integrationTest`로만 실행된다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class FavoriteServiceMariaDbIT {

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
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/favorite-race-schema.sql");
    }

    @Autowired
    private FavoriteService favoriteService;
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
    void 같은_회원이_같은_단지를_두_번_동시에_관심등록하면_하나만_성공하고_다른_하나는_DuplicateFavoriteException으로_응답한다()
            throws Exception {
        User user = userRepository.saveAndFlush(
                User.createUser("fav-race-property@test.com", "encoded", "경쟁회원"));
        Complex complex = complexRepository.saveAndFlush(complex("SRC-FAV-RACE-1"));
        Long userId = user.getUserId();
        Long complexId = complex.getComplexId();

        CountDownLatch insertedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Thread A: existsBy(miss) -> INSERT까지만 실행하고, releaseLatch가 열릴 때까지 커밋하지
            // 않고 트랜잭션을 붙잡아 둔다 — "existsBy() 조회 이후 이 요청이 INSERT하기 전에 다른 요청이
            // 먼저 INSERT를 끝낸" 상황에서 "먼저 끝낸 쪽"을 실제 트랜잭션으로 재현한다.
            Future<?> holderFuture = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                assertThat(favoritePropertyRepository.existsByUser_UserIdAndComplex_ComplexId(userId, complexId))
                        .isFalse();
                User userRef = userRepository.getReferenceById(userId);
                Complex complexRef = complexRepository.getReferenceById(complexId);
                favoritePropertyRepository.saveAndFlush(FavoriteProperty.register(userRef, complexRef, HousingType.APT));
                insertedLatch.countDown();
                awaitUninterruptibly(releaseLatch);
            }));

            // A가 INSERT까지는 마쳤지만 아직 커밋 전이라는 걸 확인한 뒤, 같은 조합으로 실제 프로덕션
            // 경로(FavoriteService.addFavoriteProperty())를 호출한다. InnoDB 기본 격리수준(REPEATABLE
            // READ)이라 B의 existsBy()는 A의 미확정 INSERT를 보지 못해 false를 받고, save()를 시도하다
            // A가 쥔 미확정 UNIQUE 인덱스 항목에 걸려 블록된다.
            assertThat(insertedLatch.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> loserFuture = executor.submit(() -> assertThatThrownBy(() -> favoriteService
                    .addFavoriteProperty(userId, new AddFavoritePropertyCommand(complexId)))
                    .isInstanceOf(DuplicateFavoriteException.class));

            // B가 existsBy를 지나 블로킹 INSERT에 도달할 시간을 준 뒤 A를 풀어 커밋시킨다 — A가 커밋되는
            // 순간 B의 블록된 INSERT가 재개되며 실제 UNIQUE 위반으로 실패하고,
            // FavoriteService.addFavoriteProperty()의 catch(DataIntegrityViolationException) 경로를
            // 실제로 태운다.
            Thread.sleep(500);
            releaseLatch.countDown();
            holderFuture.get(10, TimeUnit.SECONDS);
            loserFuture.get(10, TimeUnit.SECONDS);

            List<FavoriteProperty> rows = favoritePropertyRepository.findByUser_UserId(userId);
            assertThat(rows).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 같은_회원이_같은_지역을_두_번_동시에_관심등록하면_하나만_성공하고_다른_하나는_DuplicateFavoriteRegionException으로_응답한다()
            throws Exception {
        User user = userRepository.saveAndFlush(
                User.createUser("fav-race-region@test.com", "encoded", "경쟁회원2"));
        LegalDistrictCode region = legalDistrictCodeRepository.saveAndFlush(region("1168099999"));
        Long userId = user.getUserId();
        String legalDongCd = region.getLegalDongCd();

        CountDownLatch insertedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holderFuture = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                assertThat(favoriteRegionRepository
                        .existsByUser_UserIdAndLegalDistrictCode_LegalDongCd(userId, legalDongCd)).isFalse();
                User userRef = userRepository.getReferenceById(userId);
                LegalDistrictCode regionRef = legalDistrictCodeRepository.getReferenceById(legalDongCd);
                favoriteRegionRepository.saveAndFlush(FavoriteRegion.register(userRef, regionRef));
                insertedLatch.countDown();
                awaitUninterruptibly(releaseLatch);
            }));

            assertThat(insertedLatch.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> loserFuture = executor.submit(() -> assertThatThrownBy(() -> favoriteService
                    .addFavoriteRegion(userId, new AddFavoriteRegionCommand(legalDongCd)))
                    .isInstanceOf(DuplicateFavoriteRegionException.class));

            Thread.sleep(500);
            releaseLatch.countDown();
            holderFuture.get(10, TimeUnit.SECONDS);
            loserFuture.get(10, TimeUnit.SECONDS);

            List<FavoriteRegion> rows = favoriteRegionRepository.findByUser_UserId(userId);
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
