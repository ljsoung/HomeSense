package com.jiseong.homesense.batch.geocoding;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ComplexGeocodingService}의 수동 실행 진입점(기존 단지 좌표 백필). {@code backfill-geocoding} 프로필로만 활성화된다.
 * 기본은 dry-run — 카카오를 호출하지 않고 정제 결과만 센다. {@code --apply}를 붙여야 호출하고 좌표를 쓴다.
 * {@code --limit=N}을 붙이면 카카오에 보낼 단지 수를 N으로 제한한다(표본 확인용). {@code --retry-failed}를 붙이면 "못 찾음"
 * 표시(Redis {@code geo:fail}, TTL 30일)가 남아 있는 단지만 다시 처리한다 — 키워드 보완을 도입하기 전에 실패한 단지를
 * 표시를 수동으로 지우지 않고 다시 돌리는 경로다.
 *
 * <p>좌표가 NULL인 단지만 고르므로 중단 후 같은 명령을 다시 실행하면 남은 단지부터 이어진다. 일일 호출 상한
 * ({@code homesense.geocoding.daily-limit})에 닿으면 멈추고, 다음 날 다시 실행하거나 03:00 정기 런이 이어서 처리한다.
 *
 * <p>legal_dong_cd 백필 러너와 같이 웹 서버·{@code @Scheduled}를 띄우지 않고, 끝나면 종료 코드를 반환한다 —
 * 성공 0(일일 상한 도달 포함), 실패 1(401·403 인증 오류, 429 카카오 요청 한도, 예외).
 */
@Slf4j
@Component
@Profile("backfill-geocoding")
@RequiredArgsConstructor
public class ComplexGeocodingCommandLineRunner implements CommandLineRunner {

    private static final String APPLY_ARG = "--apply";
    private static final String LIMIT_PREFIX = "--limit=";
    private static final String RETRY_FAILED_ARG = "--retry-failed";

    private final ComplexGeocodingService geocodingService;
    private final ApplicationContext applicationContext;

    @Override
    public void run(String... args) {
        int exitCode = execute(Arrays.asList(args));
        System.exit(SpringApplication.exit(applicationContext, () -> exitCode));
    }

    int execute(List<String> args) {
        try {
            if (!args.contains(APPLY_ARG)) {
                geocodingService.dryRun();
                return 0;
            }
            int limit = args.stream()
                    .filter(arg -> arg.startsWith(LIMIT_PREFIX))
                    .map(arg -> Integer.parseInt(arg.substring(LIMIT_PREFIX.length())))
                    .findFirst()
                    .orElse(Integer.MAX_VALUE);
            ComplexGeocodingService.RunResult result = geocodingService.run(limit, args.contains(RETRY_FAILED_ARG));
            return switch (result.stopReason()) {
                case CONFIGURATION_ERROR, RATE_LIMITED, INTERRUPTED -> 1;
                default -> 0;
            };
        } catch (RuntimeException e) {
            log.error("[geocoding] 실패", e);
            return 1;
        }
    }
}
