package com.jiseong.homesense.batch.scheduler;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.batch.repository.BatchLogRepository;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * lawd_cd 커버리지 공백 소급 수집(백필)의 수동 실행 진입점. {@code backfill-lawd-cd} 프로필로만
 * 활성화되므로 평소 부팅(local/prod)에는 전혀 관여하지 않는다 —
 * {@code --spring.profiles.active=local,backfill-lawd-cd}로만 수동 실행한다.
 *
 * <p>화성시 신설 일반구(2026-02-01 개편)·인천 신설 자치구+전남광주통합특별시(2026-07-01 개편)로
 * legal_district_code가 재적재되며 새로 활성화된 36개 sgg_cd는 지금까지 BAT-SCH-01 정규 순회에
 * 한 번도 포함된 적이 없다(재적재 이전에는 그 코드 자체가 존재하지 않았으므로) — trade에 해당
 * 지역 실거래가 전혀 없는 상태다. 이 진입점이 그 공백을 한 번에 메운다.
 *
 * <p>대상 sgg_cd는 하드코딩하지 않고 {@code legal_district_code}(현재 활성 코드)와
 * {@code batch_log}(정규 배치가 실제로 순회한 적 있는 코드) 두 테이블의 차집합으로 도출한다 —
 * legal_district_code는 재적재 시 과거 스냅샷을 남기지 않아(자연키 upsert) 그 자체만으로는
 * "재적재 이전 목록"을 복원할 수 없지만, 정규 배치가 실행될 때마다 순회했던 코드를 그대로 기록해온
 * batch_log가 그 이전 목록의 산 증거 역할을 한다.
 *
 * <p>백필 기간은 두 개편일(2026-02-01/2026-07-01) 중 이른 쪽부터 오늘이 속한 월까지 36개 코드
 * 전체에 균일하게 적용한다 — 실제 API 호출로 확인한 결과(개편 이전 월을 새 코드로 조회해도
 * resultCode=000과 함께 실거래 데이터가 정상 반환되고, 반대로 구코드로 조회하면 항상 0건) 코드별로
 * 정확한 개편일을 따로 관리할 필요가 없다. 사전에 trade를 직접 조회해 이 32개 구코드로 수집된
 * 행이 과거에도 전혀 없었음을 확인했으므로(중복 적재 위험 없음), 별도 dedup 로직도 추가하지 않는다.
 *
 * <p>기존 BAT-SCH-01({@link BatchExecutionOrchestrator})/BAT-CLC-01의 조합 순회·스로틀링·재시도
 * 큐·batch_log 기록 로직을 그대로 재사용한다(새 배치 프레임워크를 만들지 않는다) —
 * {@link BatchExecutionOrchestrator#orchestrateBackfill(List, List)}만 이 목적으로 새로 얹었다.
 *
 * <p>{@code --code=<sggCd> --month=<yyyyMM>} 인자를 함께 주면 그 단일 조합만 실행한다 — 전체 확대
 * 실행 전 시범 실행(코드 1개 × 월 1개)으로 정상 적재를 먼저 확인할 때 쓴다.
 */
@Slf4j
@Component
@Profile("backfill-lawd-cd")
@RequiredArgsConstructor
public class BackfillCommandLineRunner implements CommandLineRunner {

    // 두 개편일(2026-02-01/2026-07-01) 중 이른 쪽 — 36개 코드 전체에 균일하게 이 달부터 적용한다.
    private static final YearMonth BACKFILL_START_MONTH = YearMonth.of(2026, 2);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DEAL_YMD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String CODE_ARG_PREFIX = "--code=";
    private static final String MONTH_ARG_PREFIX = "--month=";

    private final LegalDistrictCodeRepository legalDistrictCodeRepository;
    private final BatchLogRepository batchLogRepository;
    private final BatchExecutionOrchestrator orchestrator;

    @Override
    public void run(String... args) {
        List<String> argList = Arrays.asList(args);
        Optional<String> singleCode = findArgValue(argList, CODE_ARG_PREFIX);
        // --month=<yyyyMM>는 여러 번 반복해 지정할 수 있다 — 특정 코드만 일부 월이 누락됐을 때
        // (예: 시범 실행이 batch_log에 1개월치를 이미 남겨 정규 차집합 도출에서 그 코드가 통째로
        // 빠지는 경우) 보충 실행으로 나머지 달만 콕 집어 채우기 위함이다.
        List<YearMonth> explicitMonths = argList.stream()
                .filter(arg -> arg.startsWith(MONTH_ARG_PREFIX))
                .map(arg -> arg.substring(MONTH_ARG_PREFIX.length()))
                .map(month -> YearMonth.parse(month, DEAL_YMD_FORMATTER))
                .collect(Collectors.toList());

        List<String> targetCodes = singleCode.map(List::of).orElseGet(this::resolveNewlyActivatedSggCds);
        List<YearMonth> targetMonths = explicitMonths.isEmpty() ? resolveBackfillMonths() : explicitMonths;

        log.info("BAT-SCH-01 백필 시작: 대상 코드 {}건={}, 대상 월 {}건={}",
                targetCodes.size(), targetCodes, targetMonths.size(), targetMonths);
        orchestrator.orchestrateBackfill(targetCodes, targetMonths);
    }

    /**
     * legal_district_code(현재 활성 코드) - batch_log(정규 배치가 이미 순회한 적 있는 코드) 차집합 —
     * "재적재로 새로 활성화됐지만 정규 배치가 한 번도 다루지 않은 코드"만 정확히 남는다.
     */
    private List<String> resolveNewlyActivatedSggCds() {
        Set<String> alreadyQueried = new HashSet<>(batchLogRepository.findDistinctLawdCd());
        return legalDistrictCodeRepository.findDistinctActiveSggCd().stream()
                .filter(sggCd -> !alreadyQueried.contains(sggCd))
                .sorted()
                .collect(Collectors.toList());
    }

    private List<YearMonth> resolveBackfillMonths() {
        YearMonth end = YearMonth.now(KST);
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = BACKFILL_START_MONTH; !month.isAfter(end); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    private Optional<String> findArgValue(List<String> argList, String prefix) {
        return argList.stream()
                .filter(arg -> arg.startsWith(prefix))
                .map(arg -> arg.substring(prefix.length()))
                .findFirst();
    }
}
