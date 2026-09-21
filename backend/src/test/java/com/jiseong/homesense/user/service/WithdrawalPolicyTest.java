package com.jiseong.homesense.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.common.config.WithdrawalProperties;

class WithdrawalPolicyTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static WithdrawalPolicy policyAt(LocalDateTime kstNow, int graceDays) {
        return new WithdrawalPolicy(Clock.fixed(kstNow.atZone(KST).toInstant(), KST),
                new WithdrawalProperties(graceDays, new WithdrawalProperties.Purge(true, "0 0 5 * * *")));
    }

    @Test
    void graceThreshold는_현재에서_유예일수를_뺀_값이다() {
        WithdrawalPolicy policy = policyAt(LocalDateTime.of(2026, 9, 21, 5, 0, 0), 7);

        assertThat(policy.graceThreshold()).isEqualTo(LocalDateTime.of(2026, 9, 14, 5, 0, 0));
        assertThat(policy.graceDays()).isEqualTo(7);
    }

    @Test
    void 이미_계산한_now를_넘기면_그_now를_기준으로_한다() {
        WithdrawalPolicy policy = policyAt(LocalDateTime.of(2026, 9, 21, 5, 0, 0), 3);

        assertThat(policy.graceThreshold(LocalDateTime.of(2026, 1, 10, 0, 0, 0)))
                .isEqualTo(LocalDateTime.of(2026, 1, 7, 0, 0, 0));
    }

    @Test
    void now는_JVM_기본_타임존이_아니라_Clock의_KST를_따른다() {
        // 같은 순간(UTC 2026-09-20T20:00Z)을 KST Clock으로 읽으면 09-21 05:00이다.
        Clock kstClock = Clock.fixed(LocalDateTime.of(2026, 9, 20, 20, 0, 0).toInstant(ZoneOffset.UTC), KST);
        WithdrawalPolicy policy = new WithdrawalPolicy(kstClock,
                new WithdrawalProperties(7, new WithdrawalProperties.Purge(true, "0 0 5 * * *")));

        assertThat(policy.now()).isEqualTo(LocalDateTime.of(2026, 9, 21, 5, 0, 0));
    }

    @Test
    void 파기_경계와_철회_경계는_같은_threshold를_공유하며_서로_겹치지도_비지도_않는다() {
        WithdrawalPolicy policy = policyAt(LocalDateTime.of(2026, 9, 21, 5, 0, 0), 7);
        LocalDateTime threshold = policy.graceThreshold();

        LocalDateTime exactlyNDaysAgo = threshold;
        LocalDateTime oneSecondNewer = threshold.plusSeconds(1);

        // 파기 대상: withdrawn_at <= threshold / 철회 가능: withdrawn_at > threshold
        assertThat(!exactlyNDaysAgo.isAfter(threshold)).as("정확히 N일 경과 → 파기 대상").isTrue();
        assertThat(exactlyNDaysAgo.isAfter(threshold)).as("정확히 N일 경과 → 철회 불가").isFalse();
        assertThat(!oneSecondNewer.isAfter(threshold)).as("1초 덜 경과 → 파기 대상 아님").isFalse();
        assertThat(oneSecondNewer.isAfter(threshold)).as("1초 덜 경과 → 철회 가능").isTrue();
    }
}
