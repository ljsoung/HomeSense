package com.jiseong.homesense.user.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.common.config.WithdrawalProperties;

import lombok.RequiredArgsConstructor;

/**
 * 탈퇴 유예기간의 시간 계산을 한 곳에 모은다. 탈퇴 기록({@code User.withdrawnAt}), 자동 파기(BAT-USR-01),
 * 탈퇴 철회(SVC-AUTH-01.reactivate)가 전부 이 클래스의 {@link #now()}·{@link #graceThreshold}를 써서
 * 같은 시간 소스(KST {@code Clock})와 같은 경계 정의를 공유한다.
 *
 * <ul>
 *   <li>파기 대상: {@code withdrawn_at <= threshold}</li>
 *   <li>철회 가능: {@code withdrawn_at > threshold}</li>
 * </ul>
 * 두 조건은 서로 겹치지도 비지도 않는다 — 정확히 N일이 지난 계정은 파기 쪽, 1초라도 덜 지났으면 철회 쪽이다.
 */
@Component
@RequiredArgsConstructor
public class WithdrawalPolicy {

    private final Clock clock;
    private final WithdrawalProperties properties;

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDateTime graceThreshold() {
        return graceThreshold(now());
    }

    /** 이미 계산해 둔 {@code now}를 재사용해, 한 요청 안에서 threshold와 갱신 시각이 어긋나지 않게 한다. */
    public LocalDateTime graceThreshold(LocalDateTime now) {
        return now.minusDays(properties.graceDays());
    }

    public int graceDays() {
        return properties.graceDays();
    }
}
