package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * COM-CFG-01 방식. homesense.withdrawal.* 설정을 바인딩한다 — 탈퇴 계정의 유예기간과 자동 파기 배치(BAT-USR-01).
 *
 * <p>{@code graceDays}는 탈퇴 후 철회할 수 있는(그동안 파기되지 않는) 기간이다. 파기·철회의 경계는 서로
 * 겹치지도 비지도 않게 {@code withdrawn_at <= now - graceDays}(파기 대상) /
 * {@code withdrawn_at > now - graceDays}(철회 가능)로 나뉜다 — 두 조건 모두
 * {@link com.jiseong.homesense.user.service.WithdrawalPolicy}가 계산한 같은 threshold를 쓴다.
 * {@code @Min(1)} 위반이나 숫자가 아닌 값은 기동 시점에 바인딩 실패로 즉시 중단된다(fail-fast).
 *
 * <p>{@code @DefaultValue}는 프로퍼티가 아예 없는 환경(테스트 컨텍스트 등)에서도 기본값(7일, 매일 05:00,
 * 활성)으로 바인딩되게 한다. 실제 배포 값은 application.properties의 환경변수 플레이스홀더가 정한다.
 */
@ConfigurationProperties(prefix = "homesense.withdrawal")
@Validated
public record WithdrawalProperties(
        @DefaultValue("7") @Min(1) int graceDays,
        @DefaultValue @Valid Purge purge) {

    /** BAT-USR-01 스케줄 설정. {@code enabled=false}이면 스케줄러 빈 자체가 등록되지 않는다. */
    public record Purge(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("0 0 5 * * *") @NotBlank String cron) {
    }
}
