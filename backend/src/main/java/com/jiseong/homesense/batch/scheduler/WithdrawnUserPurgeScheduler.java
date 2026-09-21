package com.jiseong.homesense.batch.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jiseong.homesense.user.service.WithdrawalPolicy;
import com.jiseong.homesense.user.service.WithdrawnUserPurgeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BAT-USR-01. 탈퇴 후 유예기간({@code homesense.withdrawal.grace-days}, 기본 7일)이 지난 회원을 매일 새벽
 * (기본 05:00 KST — 03:00에 시작하는 수집→알림→메일 체인과 겹치지 않게 잡은 값) 물리 삭제한다.
 *
 * <p>이 클래스는 {@code @Scheduled} 진입점이자 반복문만 갖고, 트랜잭션은 걸지 않는다. 사용자 1명당 별도
 * 트랜잭션은 다른 빈({@link WithdrawnUserPurgeService#purgeOne})이 맡는다. 한 명이 실패해도 다음 사람으로
 * 진행하고, keyset 커서({@code afterId})가 항상 앞으로만 움직여 실패한 행이 있어도 반복이 끝난다.
 * 다중 인스턴스 락은 두지 않는다 — 조건부 DELETE가 멱등이라 중복 실행에도 안전하다.
 *
 * <p>{@code enabled=false}이면 이 빈 자체가 등록되지 않는다(로컬에서 테스트 계정이 며칠 뒤 조용히
 * 삭제되는 것을 막기 위해 application-local.properties가 끈다). 로그에는 userId와 집계 수치만 남기고
 * 이메일·닉네임은 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "homesense.withdrawal.purge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WithdrawnUserPurgeScheduler {

    public static final int CHUNK_SIZE = 100;

    private final WithdrawnUserPurgeService purgeService;
    private final WithdrawalPolicy withdrawalPolicy;
    private final Clock clock;

    @Scheduled(cron = "${homesense.withdrawal.purge.cron:0 0 5 * * *}", zone = "Asia/Seoul")
    public void runDailyPurge() {
        long startedAtMillis = clock.millis();
        LocalDateTime threshold = withdrawalPolicy.graceThreshold();

        long malformedWithdrawn = purgeService.countWithdrawnWithoutTimestamp();
        if (malformedWithdrawn > 0) {
            // 관리자 경로 등에서 status=WITHDRAWN인데 withdrawn_at이 비어 있는 행이 생길 수 있다 — 언제
            // 탈퇴했는지 알 수 없어 유예기간 경과 여부를 판단할 수 없으므로 절대 삭제하지 않고 알리기만 한다.
            log.atWarn()
                    .addKeyValue("auditEvent", "WITHDRAWN_USER_PURGE_MALFORMED")
                    .addKeyValue("malformedWithdrawn", malformedWithdrawn)
                    .log("WITHDRAWN_USER_PURGE skipped rows with status=WITHDRAWN and withdrawn_at IS NULL count={}",
                            malformedWithdrawn);
        }

        int purged = 0;
        int skipped = 0;
        int failed = 0;
        long afterId = 0L;
        while (true) {
            List<Long> ids = purgeService.findTargetIds(threshold, afterId, CHUNK_SIZE);
            if (ids.isEmpty()) {
                break;
            }
            for (Long userId : ids) {
                try {
                    if (purgeService.purgeOne(userId, threshold) > 0) {
                        purged++;
                    } else {
                        skipped++; // 조회 이후 철회됐거나 다른 실행이 이미 삭제했다
                    }
                } catch (RuntimeException e) {
                    failed++;
                    log.atError()
                            .addKeyValue("auditEvent", "WITHDRAWN_USER_PURGE_FAILURE")
                            .addKeyValue("userId", userId)
                            .setCause(e)
                            .log("WITHDRAWN_USER_PURGE failed userId={}", userId);
                }
            }
            afterId = ids.get(ids.size() - 1);
        }

        log.atInfo()
                .addKeyValue("auditEvent", "WITHDRAWN_USER_PURGE")
                .addKeyValue("purged", purged)
                .addKeyValue("skipped", skipped)
                .addKeyValue("failed", failed)
                .addKeyValue("malformedWithdrawn", malformedWithdrawn)
                .addKeyValue("graceDays", withdrawalPolicy.graceDays())
                .addKeyValue("durationMs", clock.millis() - startedAtMillis)
                .log("WITHDRAWN_USER_PURGE completed purged={} skipped={} failed={} malformedWithdrawn={} graceDays={}",
                        purged, skipped, failed, malformedWithdrawn, withdrawalPolicy.graceDays());
    }
}
