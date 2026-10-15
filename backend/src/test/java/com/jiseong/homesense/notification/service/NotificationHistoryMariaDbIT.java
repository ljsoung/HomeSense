package com.jiseong.homesense.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.notification.dto.NotificationResponse;
import com.jiseong.homesense.notification.entity.NotificationType;
import com.jiseong.homesense.notification.exception.AccessDeniedException;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

/**
 * SVC-NTF-01 알림 이력 조회(MY-04)를 실제 MariaDB로 확인한다 — 정렬 고정·보조 정렬(같은 created_at이 페이지 경계를 넘을 때
 * 중복·누락 없음), 조회 기간 창(6개월 경계 포함·제외), 요청 정렬 무시, 페이지 크기 상한, 읽음 처리(타인·두 번·기간 밖).
 * 정렬·경계는 쿼리가 실제 DB에서 어떤 행을 돌려주는지의 문제라 Mockito로는 확인할 수 없다.
 *
 * <p>알림은 created_at을 정해 넣어야 해 JdbcTemplate으로 넣는다. 기간 경계는 서비스와 같은 JVM 기본 타임존의 현재 시각
 * 기준이다(NotificationService.getNotifications 주석). Docker가 필요해 `./gradlew integrationTest`로만 실행된다.
 */
@SpringBootTest
@Testcontainers
@Tag("integration")
class NotificationHistoryMariaDbIT {

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
        registry.add("homesense.scheduling.enabled", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private UserRepository userRepository;

    private Long userId;
    private Long otherUserId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM notification");
        jdbc.update("DELETE FROM user");
        userId = userRepository.saveAndFlush(User.createUser("history@test.com", "encoded", "이력회원")).getUserId();
        otherUserId = userRepository.saveAndFlush(User.createUser("history-other@test.com", "encoded", "다른회원")).getUserId();
    }

    private long insert(Long owner, NotificationType type, LocalDateTime createdAt, boolean read) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO notification (user_id, notification_type, title, is_read, created_at)
                    VALUES (?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, owner);
            ps.setString(2, type.name());
            ps.setString(3, type.name() + " 알림");
            ps.setBoolean(4, read);
            ps.setTimestamp(5, Timestamp.valueOf(createdAt));
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private List<Long> ids(Page<NotificationResponse> page) {
        return page.getContent().stream().map(NotificationResponse::notificationId).toList();
    }

    @Test
    void created_at이_같은_알림이_페이지_경계를_넘어도_중복_누락_없이_ID_역순으로_나뉜다() {
        // 같은 배치 런 — 25건이 같은 초. 앞뒤로 다른 시각 1건씩.
        LocalDateTime run = LocalDateTime.now().minusDays(1).withNano(0);
        long newest = insert(userId, NotificationType.NEW_TRADE, run.plusHours(1), false);
        List<Long> sameSecond = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            sameSecond.add(insert(userId, i % 2 == 0 ? NotificationType.NEW_TRADE : NotificationType.PRICE_CHANGE, run, false));
        }
        long oldest = insert(userId, NotificationType.PRICE_CHANGE, run.minusHours(1), false);

        List<Long> collected = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            collected.addAll(ids(notificationService.getNotifications(userId, null, PageRequest.of(page, 10))));
        }

        List<Long> expected = new ArrayList<>();
        expected.add(newest);
        expected.addAll(sameSecond.reversed());
        expected.add(oldest);
        assertThat(collected).containsExactlyElementsOf(expected);
    }

    @Test
    void 요청의_정렬은_무시하고_발생_시각_최신순을_지킨다() {
        LocalDateTime base = LocalDateTime.now().minusDays(2).withNano(0);
        long older = insert(userId, NotificationType.NEW_TRADE, base, false);
        long newer = insert(userId, NotificationType.NEW_TRADE, base.plusMinutes(5), false);

        Page<NotificationResponse> page = notificationService.getNotifications(
                userId, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "notificationId")));

        assertThat(ids(page)).containsExactly(newer, older);
    }

    @Test
    void 페이지_크기는_50건까지다() {
        LocalDateTime base = LocalDateTime.now().minusDays(1).withNano(0);
        for (int i = 0; i < 60; i++) {
            insert(userId, NotificationType.NEW_TRADE, base.minusMinutes(i), false);
        }

        Page<NotificationResponse> page = notificationService.getNotifications(userId, null, PageRequest.of(0, 100));

        assertThat(page.getContent()).hasSize(NotificationService.MAX_PAGE_SIZE);
        assertThat(page.getTotalElements()).isEqualTo(60);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void 발생_후_6개월이_지난_알림은_목록과_건수에서_빠지고_경계_안쪽은_남는다() {
        LocalDateTime boundary = LocalDateTime.now().minusMonths(6);
        long inside = insert(userId, NotificationType.PRICE_CHANGE, boundary.plusMinutes(5), false);
        insert(userId, NotificationType.PRICE_CHANGE, boundary.minusMinutes(5), false);
        insert(userId, NotificationType.NEW_TRADE, boundary.minusMonths(1), false);

        Page<NotificationResponse> all = notificationService.getNotifications(userId, null, PageRequest.of(0, 10));
        Page<NotificationResponse> priceChange = notificationService.getNotifications(
                userId, NotificationType.PRICE_CHANGE, PageRequest.of(0, 10));
        Page<NotificationResponse> newTrade = notificationService.getNotifications(
                userId, NotificationType.NEW_TRADE, PageRequest.of(0, 10));

        assertThat(ids(all)).containsExactly(inside);
        assertThat(all.getTotalElements()).isEqualTo(1);
        assertThat(ids(priceChange)).containsExactly(inside);
        assertThat(newTrade.getTotalElements()).isZero();
        // 데이터는 지우지 않는다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ?", Long.class, userId)).isEqualTo(3);
    }

    @Test
    void 남의_알림은_읽음처리할_수_없고_같은_알림을_두_번_읽음처리해도_그대로다() {
        long mine = insert(userId, NotificationType.NEW_TRADE, LocalDateTime.now().minusHours(1), false);
        long others = insert(otherUserId, NotificationType.NEW_TRADE, LocalDateTime.now().minusHours(1), false);

        assertThatThrownBy(() -> notificationService.markAsRead(userId, others)).isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForObject("SELECT is_read FROM notification WHERE notification_id = ?", Boolean.class, others)).isFalse();

        notificationService.markAsRead(userId, mine);
        notificationService.markAsRead(userId, mine);
        assertThat(jdbc.queryForObject("SELECT is_read FROM notification WHERE notification_id = ?", Boolean.class, mine)).isTrue();
    }

    @Test
    void 조회_기간_밖의_알림도_본인_것이면_읽음처리된다() {
        long old = insert(userId, NotificationType.PRICE_CHANGE, LocalDateTime.now().minusMonths(7), false);

        notificationService.markAsRead(userId, old);

        assertThat(jdbc.queryForObject("SELECT is_read FROM notification WHERE notification_id = ?", Boolean.class, old)).isTrue();
    }
}
