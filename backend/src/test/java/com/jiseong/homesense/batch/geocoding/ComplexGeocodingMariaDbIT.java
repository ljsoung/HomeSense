package com.jiseong.homesense.batch.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jiseong.homesense.batch.geocoding.ComplexGeocodingService.StopReason;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;

/**
 * BAT-GEO-01 좌표 쓰기를 실제 MariaDB로 검증한다 — 조건부 네이티브 UPDATE({@code latitude IS NULL})가 이미 좌표가 있는
 * 단지를 덮지 않는지, 중단 후 다시 실행하면 남은 단지부터 이어지는지는 Mockito로 증명할 수 없다.
 *
 * <p>카카오 클라이언트와 Redis 상태({@link GeocodingRedisState})는 목으로 둔다. 이 IT는 개발용 로컬 Redis를 쓰게 되는데
 * (CLAUDE.md 백로그의 알려진 격리 한계), 실제 Redis 상태를 쓰면 오늘 호출 카운터(geo:calls)가 늘어 실제 쿼터 계산을
 * 흐린다. Redis 동작은 GeocodingRedisStateTest가 맡는다.
 */
@SpringBootTest
@Testcontainers
@Transactional
@Tag("integration")
class ComplexGeocodingMariaDbIT {

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
        registry.add("spring.sql.init.schema-locations", () -> "classpath:testcontainers/complex-search-schema.sql");
        registry.add("homesense.geocoding.request-interval-millis", () -> "0");
        registry.add("homesense.geocoding.retry-backoff-millis", () -> "0");
    }

    private static final KakaoGeocodingClient.Found JONGNO =
            new KakaoGeocodingClient.Found(new BigDecimal("37.5726145"), new BigDecimal("126.9722730"));

    @Autowired
    private ComplexGeocodingService geocodingService;
    @Autowired
    private ComplexRepository complexRepository;

    @MockitoBean
    private KakaoGeocodingClient client;
    @MockitoBean
    private GeocodingRedisState redisState;

    /** 키워드 검색 마지막 페이지(is_end=true) 하나. */
    private static KakaoGeocodingClient.KeywordPage lastPage(KakaoGeocodingClient.KeywordPlace... places) {
        return new KakaoGeocodingClient.KeywordPage(List.of(places), true);
    }

    private Complex complex(String sourceCd, String address, String dongRi, BigDecimal latitude, BigDecimal longitude) {
        return complexRepository.saveAndFlush(Complex.builder()
                .sourceComplexCd(sourceCd).complexName(sourceCd).complexType("아파트")
                .sido("서울특별시").sigungu("종로구").dongRi(dongRi).legalDongAddress(address)
                .latitude(latitude).longitude(longitude).locationPrecision(latitude == null ? null : "PRECISE")
                .elevatorPassengerCount((short) 1).elevatorCargoCount((short) 0).elevatorCombinedCount((short) 0)
                .dataUpdatedAt(LocalDate.of(2026, 1, 1)).build());
    }

    @Test
    void 좌표를_쓰고_이미_좌표가_있는_단지와_지번_없는_단지는_건드리지_않는다() {
        Complex target = complex("GEO-1", "서울특별시 종로구 내수동 73- 경희궁의아침4단지", "내수동", null, null);
        Complex existing = complex("GEO-2", "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동",
                new BigDecimal("37.0000000"), new BigDecimal("127.0000000"));
        Complex noJibun = complex("GEO-3", "서울특별시 종로구 내수동  신축아파트", "내수동", null, null);
        when(client.geocode("서울특별시 종로구 내수동 73", false)).thenReturn(JONGNO);
        when(redisState.recordCall()).thenReturn(1L);

        ComplexGeocodingService.RunResult result = geocodingService.run(Integer.MAX_VALUE);

        assertThat(result.written()).isEqualTo(1);
        assertThat(result.noJibun()).isEqualTo(1);
        Complex reloaded = complexRepository.findById(target.getComplexId()).orElseThrow();
        assertThat(reloaded.getLatitude()).isEqualByComparingTo("37.5726145");
        assertThat(reloaded.getLongitude()).isEqualByComparingTo("126.9722730");
        assertThat(reloaded.getLocationPrecision()).isEqualTo("PRECISE");
        assertThat(complexRepository.findById(existing.getComplexId()).orElseThrow().getLatitude())
                .isEqualByComparingTo("37.0000000");
        assertThat(complexRepository.findById(noJibun.getComplexId()).orElseThrow().getLatitude()).isNull();
        verify(client, times(1)).geocode(anyString(), anyBoolean());
    }

    @Test
    void 중간에_멈춘_뒤_다시_실행하면_남은_단지부터_이어간다() {
        Complex first = complex("GEO-A", "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동", null, null);
        Complex second = complex("GEO-B", "서울특별시 종로구 내수동 73 경희궁의아침4단지", "내수동", null, null);
        when(client.geocode(anyString(), anyBoolean())).thenReturn(JONGNO);
        when(redisState.recordCall()).thenReturn(1L);

        ComplexGeocodingService.RunResult firstRun = geocodingService.run(1);
        assertThat(firstRun.stopReason()).isEqualTo(StopReason.MAX_COMPLEXES);
        assertThat(complexRepository.findById(first.getComplexId()).orElseThrow().getLatitude()).isNotNull();
        assertThat(complexRepository.findById(second.getComplexId()).orElseThrow().getLatitude()).isNull();

        ComplexGeocodingService.RunResult secondRun = geocodingService.run(Integer.MAX_VALUE);

        assertThat(secondRun.targets()).isEqualTo(1);
        assertThat(secondRun.written()).isEqualTo(1);
        assertThat(complexRepository.findById(second.getComplexId()).orElseThrow().getLatitude()).isNotNull();
        verify(client, times(1)).geocode("서울특별시 종로구 내수동 72", false);
        verify(client, times(1)).geocode("서울특별시 종로구 내수동 73", false);
    }

    @Test
    void 좌표_쓰기는_이미_좌표가_있으면_덮지_않는다() {
        Complex existing = complex("GEO-X", "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동",
                new BigDecimal("37.0000000"), new BigDecimal("127.0000000"));

        int updated = complexRepository.fillCoordinatesIfNull(existing.getComplexId(), JONGNO.latitude(),
                JONGNO.longitude(), "PRECISE", LocalDateTime.now());

        assertThat(updated).isZero();
        assertThat(complexRepository.findById(existing.getComplexId()).orElseThrow().getLatitude())
                .isEqualByComparingTo("37.0000000");
        verify(client, never()).geocode(anyString(), anyBoolean());
    }

    @Test
    void 키워드_검색으로_얻은_좌표는_APPROXIMATE로_저장된다() {
        // location_precision을 VARCHAR(12)로 넓힌 스키마에서 11자 값이 실제로 들어가는지 본다(VARCHAR(10)이면 Data too long)
        Complex target = complex("GEO-K", "서울특별시 종로구 내수동 963 재건축단지", "내수동", null, null);
        when(client.geocode(anyString(), anyBoolean())).thenReturn(new KakaoGeocodingClient.NotFound("NO_RESULT"));
        when(client.searchKeyword("종로구 GEO-K", 1)).thenReturn(lastPage(new KakaoGeocodingClient.KeywordPlace(
                "GEO-K아파트", "부동산 > 주거시설 > 아파트", "서울 종로구 내수동 1032", JONGNO.latitude(), JONGNO.longitude())));
        when(redisState.recordCall()).thenReturn(1L);

        ComplexGeocodingService.RunResult result = geocodingService.run(Integer.MAX_VALUE);

        assertThat(result.foundByKeyword()).isEqualTo(1);
        Complex reloaded = complexRepository.findById(target.getComplexId()).orElseThrow();
        assertThat(reloaded.getLocationPrecision()).isEqualTo("APPROXIMATE");
        assertThat(reloaded.getLatitude()).isEqualByComparingTo("37.5726145");
    }

    @Test
    void 재판정_탈락은_APPROXIMATE만_되돌리고_PRECISE는_건드리지_않는다() {
        Complex approximate = complexRepository.saveAndFlush(Complex.builder()
                .sourceComplexCd("GEO-R").complexName("동탄파크자이아파트").complexType("아파트")
                .sido("서울특별시").sigungu("종로구").dongRi("내수동").legalDongAddress("서울특별시 종로구 내수동 1 동탄파크자이아파트")
                .latitude(new BigDecimal("37.2000000")).longitude(new BigDecimal("127.1000000")).locationPrecision("APPROXIMATE")
                .elevatorPassengerCount((short) 1).elevatorCargoCount((short) 0).elevatorCombinedCount((short) 0)
                .dataUpdatedAt(LocalDate.of(2026, 1, 1)).build());
        Complex precise = complex("GEO-P", "서울특별시 종로구 내수동 72 경희궁의아침3단지", "내수동",
                new BigDecimal("37.0000000"), new BigDecimal("127.0000000"));
        when(client.searchKeyword(anyString(), anyInt())).thenReturn(lastPage(
                new KakaoGeocodingClient.KeywordPlace("동탄역자이아파트",
                "부동산 > 주거시설 > 아파트", "서울 종로구 내수동 892", JONGNO.latitude(), JONGNO.longitude())));
        when(redisState.recordCall()).thenReturn(1L);

        ComplexGeocodingService.RecheckResult result = geocodingService.recheckApproximate(Integer.MAX_VALUE);

        assertThat(result.dropped()).isEqualTo(1);
        Complex reloaded = complexRepository.findById(approximate.getComplexId()).orElseThrow();
        assertThat(reloaded.getLatitude()).isNull();
        assertThat(reloaded.getLocationPrecision()).isNull();
        assertThat(complexRepository.clearApproximateCoordinates(precise.getComplexId(), LocalDateTime.now())).isZero();
        assertThat(complexRepository.findById(precise.getComplexId()).orElseThrow().getLatitude())
                .isEqualByComparingTo("37.0000000");
        verify(redisState).markNotFound(approximate.getComplexId(), "RECHECK/KEYWORD_NAME_MISMATCH");
    }
}
