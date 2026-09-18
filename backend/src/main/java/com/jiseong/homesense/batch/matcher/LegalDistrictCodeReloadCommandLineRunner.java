package com.jiseong.homesense.batch.matcher;

import java.io.File;
import java.io.IOException;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

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
 */
@Slf4j
@Component
@Profile("reload-legal-district")
@RequiredArgsConstructor
public class LegalDistrictCodeReloadCommandLineRunner implements CommandLineRunner {

    private static final String RESOURCE_PATH = "data/법정동코드.txt";

    private final LegalDistrictCodeLoader legalDistrictCodeLoader;

    @Override
    public void run(String... args) throws IOException {
        File csvFile = new ClassPathResource(RESOURCE_PATH).getFile();
        log.info("법정동코드 재적재 시작: file={}", csvFile.getAbsolutePath());
        legalDistrictCodeLoader.loadInitial(csvFile);
        log.info("법정동코드 재적재 완료");
    }
}
