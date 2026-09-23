package com.jiseong.homesense.batch.matcher;

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
 * {@link ComplexLegalDongBackfillService}의 수동 실행 진입점. {@code backfill-complex-legal-dong} 프로필로만
 * 활성화된다. 기본은 dry-run(DB 쓰기 없음)이고, {@code --apply}를 붙여야 실제로 갱신한다.
 * {@code --strategy=NAME|PREFIX|COMBINED}로 방식을 고른다(기본 COMBINED).
 *
 * <p>단지 기본정보를 새로 적재(또는 재적재)한 뒤에도 같은 명령으로 다시 돌리면 legal_dong_cd가 NULL인
 * 행만 채운다 — 적재 경로가 이 저장소 밖에 있어 그 경로 자체를 고칠 수 없으므로, 이 러너가 적재 후
 * 단계 역할을 한다(운영 절차: docs/runbook-complex-legal-dong-backfill.md).
 *
 * <p>이 프로필은 웹 서버와 {@code @Scheduled}를 띄우지 않는다(application-backfill-complex-legal-dong.properties).
 * 작업이 끝나면 {@link SpringApplication#exit}로 컨텍스트를 닫고 종료 코드를 반환한다 — 성공 0, 실패 1.
 */
@Slf4j
@Component
@Profile("backfill-complex-legal-dong")
@RequiredArgsConstructor
public class ComplexLegalDongBackfillCommandLineRunner implements CommandLineRunner {

    private static final String APPLY_ARG = "--apply";
    private static final String STRATEGY_PREFIX = "--strategy=";

    private final ComplexLegalDongBackfillService backfillService;
    private final ApplicationContext applicationContext;

    @Override
    public void run(String... args) {
        int exitCode = execute(Arrays.asList(args));
        System.exit(SpringApplication.exit(applicationContext, () -> exitCode));
    }

    int execute(List<String> args) {
        try {
            if (!args.contains(APPLY_ARG)) {
                backfillService.dryRun();
                return 0;
            }
            ComplexLegalDongBackfillService.Strategy strategy = args.stream()
                    .filter(arg -> arg.startsWith(STRATEGY_PREFIX))
                    .map(arg -> ComplexLegalDongBackfillService.Strategy.valueOf(arg.substring(STRATEGY_PREFIX.length())))
                    .findFirst()
                    .orElse(ComplexLegalDongBackfillService.Strategy.COMBINED);
            backfillService.apply(strategy);
            return 0;
        } catch (RuntimeException e) {
            log.error("[backfill] 실패", e);
            return 1;
        }
    }
}
