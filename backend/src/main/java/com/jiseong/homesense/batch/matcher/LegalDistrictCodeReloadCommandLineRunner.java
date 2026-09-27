package com.jiseong.homesense.batch.matcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.region.service.RegionCodePrefixResolver;
import com.jiseong.homesense.region.service.RegionCodePrefixResolver.VersionPublication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BAT-MAT-01({@link LegalDistrictCodeLoader})의 수동 재적재 진입점. {@code reload-legal-district}
 * 프로필로만 활성화되므로 평소 부팅(local/prod)에는 전혀 관여하지 않는다 — 행정안전부가 법정동코드
 * 전체자료를 갱신 배포했을 때(비정기)만 {@code --spring.profiles.active=local,reload-legal-district}로
 * 수동 실행한다.
 *
 * <p>2026년 두 차례 행정구역 개편(화성시 일반구 신설 2026-02-01, 인천 행정체제 개편·전남광주통합특별시
 * 출범 2026-07-01)을 반영한 새 참조자료를 {@code src/main/resources/data/법정동코드.txt}로 교체하며
 * 함께 만들었다 — CLAUDE.md의 이 재적재 세션 절 참고.
 *
 * <p>{@link LegalDistrictCodeLoader#loadInitial(File)}은 {@code deactivateAll()} → 이번 CSV의 "존재"
 * 행만 upsert로 재활성화하는 패턴이라 DELETE를 쓰지 않는다 — 폐지된 코드는 삭제되지 않고
 * {@code is_active=false}로 보존되므로, 그 코드를 참조하는 기존 complex/trade FK가 깨지지 않는다
 * (안전성 검증: {@code LegalDistrictCodeLoaderMariaDbIT}).
 *
 * <p>이 리소스를 {@link ClassPathResource#getFile()}로 곧바로 읽으면 안 된다 — 패키징된 JAR로
 * 실행할 때는 이 엔트리가 jar: URL(파일시스템 실체가 없음)이라 {@code getFile()}이 재적재 시작
 * 전에 예외를 던진다. 문서화된 {@code bootRun} 실행은 uncompressed classes 디렉터리를 쓰기 때문에
 * 우연히 통과했을 뿐이라, 스트림을 임시 파일로 복사해 어떤 실행 방식에서도 동작하게 한다.
 *
 * <p>재적재가 끝나면 {@link RegionCodePrefixResolver}의 prefix 맵 버전 발행(Redis
 * {@code region:prefix-map:version}) 결과를 요약 로그로 남긴다. 이 러너는 서비스 중인 서버와 다른
 * JVM이라, 발행이 실패하면 서버는 재시작 전까지 옛 prefix 맵을 쓴다. 발행 실패는 재적재 자체의 실패가
 * 아니므로 종료 코드에 반영하지 않는다 — 종료 코드는 재적재 결과만 나타낸다.
 *
 * <p>이 프로필은 웹 서버와 {@code @Scheduled}를 띄우지 않는다(application-reload-legal-district.properties) —
 * 러너가 떠 있는 동안 수집 배치(03:00)가 운영 앱과 중복 실행되지 않게 한다. 작업이 끝나면
 * {@link SpringApplication#exit}로 컨텍스트를 닫고 종료 코드를 반환한다 — 성공 0, 실패 1
 * ({@code ComplexLegalDongBackfillCommandLineRunner}와 같은 방식).
 */
@Slf4j
@Component
@Profile("reload-legal-district")
@RequiredArgsConstructor
public class LegalDistrictCodeReloadCommandLineRunner implements CommandLineRunner {

    private static final String RESOURCE_PATH = "data/법정동코드.txt";

    private final LegalDistrictCodeLoader legalDistrictCodeLoader;
    private final RegionCodePrefixResolver regionCodePrefixResolver;
    private final ApplicationContext applicationContext;

    @Override
    public void run(String... args) {
        int exitCode = execute();
        System.exit(SpringApplication.exit(applicationContext, () -> exitCode));
    }

    int execute() {
        try {
            reload();
        } catch (IOException | RuntimeException e) {
            log.error("법정동코드 재적재 실패", e);
            return 1;
        }
        logVersionPublication(regionCodePrefixResolver.lastVersionPublication().orElse(null));
        return 0;
    }

    private void reload() throws IOException {
        Path tempFile = Files.createTempFile("legal-district-code", ".txt");
        try {
            try (var in = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("법정동코드 재적재 시작: resource={}", RESOURCE_PATH);
            legalDistrictCodeLoader.loadInitial(tempFile.toFile());
            log.info("법정동코드 재적재 완료");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void logVersionPublication(VersionPublication publication) {
        if (publication == null) {
            log.warn("재적재 요약 | prefix 맵 버전 발행: 시도되지 않음(재적재 이벤트 미수신). "
                    + "서비스 중인 서버를 재시작하라");
        } else if (publication.succeeded()) {
            log.info("재적재 요약 | prefix 맵 버전 발행: 성공 (region:prefix-map:version={})",
                    publication.version());
        } else {
            log.warn("재적재 요약 | prefix 맵 버전 발행: 실패 ({}). 서비스 중인 서버를 재시작하라",
                    publication.failureReason());
        }
    }
}
