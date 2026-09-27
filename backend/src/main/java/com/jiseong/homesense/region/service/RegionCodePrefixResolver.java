package com.jiseong.homesense.region.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.jiseong.homesense.batch.matcher.LegalDistrictCodeReloadedEvent;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 법정동코드(10자리)를 "그 지역과 하위 지역 전부"를 가리키는 코드 prefix로 바꾼다 — 단지 검색의
 * {@code complex.legal_dong_cd LIKE 'prefix%'} 계층 필터(API-CPX-01 regionCode)가 쓴다.
 *
 * <p>규칙(2026-09-23 활성 코드 전수 실증, CLAUDE.md "단지 검색 지역코드·키워드" 절):
 * <ul>
 *   <li>시도(뒤 8자리 0): 앞 2자리.</li>
 *   <li>시군구 대표행(뒤 5자리 0): 앞 5자리. 단, 구를 가진 시(수원시 41110 → 41111·41113…)는 구 코드가
 *       5번째 자리에서 갈라지므로 앞 4자리. 이 13개 시는 4자리 그룹 안에 자기와 산하 구만 있다.</li>
 *   <li>읍면동(뒤 2자리 0): 앞 8자리. 리: 10자리 전체.</li>
 * </ul>
 * "구를 가진 시"인지는 코드 숫자로 판정하면 안 된다 — 영동군(43740)과 증평군(43745)은 서로 무관한데
 * 같은 4자리 {@code 4374}를 공유한다. 그래서 같은 4자리 안에 {@code "{시군구명} "}으로 시작하는 하위
 * 시군구가 있는지(이름)로 판정한다. 세종(3611000000)은 시군구 계층이 없어 시군구 대표행 규칙으로 앞
 * 5자리({@code 36110})가 되고, 하위 동 전부가 그 prefix로 시작한다.
 *
 * <p>판정용 데이터는 요청마다 조회하지 않는다. 활성 코드 전체(약 2만 건)로 코드→prefix 맵을 한 번
 * 만들어 JVM 안에 들고 있는다.
 *
 * <p><b>무효화는 프로세스 경계를 넘어야 한다.</b> 법정동코드 재적재는 서비스 중인 서버가 아니라 별도
 * JVM({@code reload-legal-district} 프로필의 {@code LegalDistrictCodeReloadCommandLineRunner})에서
 * 돌기 때문에, 재적재 이벤트({@link LegalDistrictCodeReloadedEvent})는 그 러너 프로세스 안에서만
 * 발행된다 — 이벤트로 로컬 맵만 비우면 서비스 중인 서버는 재시작 전까지 옛 맵을 계속 쓴다(코드리뷰 P2).
 * 그래서 이벤트를 받은 프로세스는 Redis 키 {@value #VERSION_KEY}에 새 버전값을 쓰고, 모든 프로세스는
 * 조회할 때마다 이 키를 읽어 자기 맵을 만든 시점의 버전과 다르면 맵을 다시 만든다. Redis
 * (regionAutocomplete 캐시)는 이미 프로세스 간에 공유되므로 같은 이벤트가 그 캐시를 비우는 것과 같은
 * 경로다. 키가 없으면(최초 기동, Redis 초기화) null을 버전으로 본다 — 초기화 직후 한 번 더 다시 만들
 * 뿐 틀린 결과는 없다. Redis를 읽지 못하면 검색을 막지 않고 들고 있는 맵을 그대로 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionCodePrefixResolver {

    static final String VERSION_KEY = "region:prefix-map:version";

    private final LegalDistrictCodeRepository legalDistrictCodeRepository;
    private final StringRedisTemplate redisTemplate;

    private volatile Snapshot snapshot;

    /** 존재하지 않거나 폐지된 코드는 empty — 호출자는 빈 결과로 처리한다. */
    public Optional<String> prefixOf(String legalDongCd) {
        return Optional.ofNullable(current().prefixByCode().get(legalDongCd));
    }

    /**
     * 재적재 트랜잭션 커밋 뒤에 버전을 바꾼다 — 커밋 전에 바꾸면 그 사이 요청이 옛 데이터로 맵을 다시
     * 만들고 새 버전을 달아 버릴 수 있다(CacheEvictionListener와 같은 이유).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLegalDistrictCodeReloaded(LegalDistrictCodeReloadedEvent event) {
        snapshot = null;
        try {
            redisTemplate.opsForValue().set(VERSION_KEY, UUID.randomUUID().toString());
        } catch (RuntimeException e) {
            // 이미 커밋된 재적재를 실패로 만들지 않는다. 다른 프로세스는 재시작 전까지 옛 맵을 쓴다.
            log.warn("법정동코드 prefix 맵 버전 갱신 실패 — 다른 서버 프로세스에는 반영되지 않는다", e);
        }
    }

    private Snapshot current() {
        Snapshot held = snapshot;
        String version;
        try {
            version = redisTemplate.opsForValue().get(VERSION_KEY);
        } catch (RuntimeException e) {
            if (held != null) {
                log.warn("법정동코드 prefix 맵 버전 조회 실패 — 보유 중인 맵을 그대로 쓴다", e);
                return held;
            }
            log.warn("법정동코드 prefix 맵 버전 조회 실패 — 버전 없이 맵을 만든다", e);
            return load(held, null);
        }
        if (held != null && Objects.equals(held.version(), version)) {
            return held;
        }
        return load(held, version);
    }

    /**
     * 버전을 먼저 읽고 맵을 만든다. 그 사이 재적재가 끝나면 새 데이터에 옛 버전이 붙는데, 다음 조회에서
     * 버전이 다르다고 보고 한 번 더 만들 뿐이라 틀린 맵이 남지 않는다.
     */
    private synchronized Snapshot load(Snapshot seen, String version) {
        Snapshot held = snapshot;
        if (held != null && held != seen && Objects.equals(held.version(), version)) {
            return held; // 기다리는 동안 다른 스레드가 같은 버전으로 이미 만들었다
        }
        Snapshot built = new Snapshot(version, computePrefixes(legalDistrictCodeRepository.findAll()));
        snapshot = built;
        return built;
    }

    private record Snapshot(String version, Map<String, String> prefixByCode) {
    }

    /** 활성 코드만 대상으로 코드→prefix 맵을 만든다(단위 테스트용으로 분리). */
    static Map<String, String> computePrefixes(List<LegalDistrictCode> codes) {
        List<LegalDistrictCode> active = codes.stream().filter(LegalDistrictCode::isActive).toList();
        Map<String, String> result = new HashMap<>();
        for (LegalDistrictCode code : active) {
            result.put(code.getLegalDongCd(), prefixOf(code, active));
        }
        return result;
    }

    private static String prefixOf(LegalDistrictCode code, List<LegalDistrictCode> active) {
        String cd = code.getLegalDongCd();
        if (cd.endsWith("00000000")) {
            return cd.substring(0, 2);
        }
        if (cd.endsWith("00000")) {
            return hasChildGu(code, active) ? cd.substring(0, 4) : cd.substring(0, 5);
        }
        if (cd.endsWith("00")) {
            return cd.substring(0, 8);
        }
        return cd;
    }

    private static boolean hasChildGu(LegalDistrictCode city, List<LegalDistrictCode> active) {
        String name = city.getSigunguName();
        if (name == null || name.contains(" ")) {
            return false;
        }
        String group = city.getLegalDongCd().substring(0, 4);
        String childPrefix = name + " ";
        return active.stream().anyMatch(other -> other.getLegalDongCd().startsWith(group)
                && other.getSigunguName() != null
                && other.getSigunguName().startsWith(childPrefix));
    }
}
