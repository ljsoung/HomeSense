package com.jiseong.homesense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/**
 * SVC-NTF-01 알림 이력(MY-04) 설정. homesense.notification.history.* 를 바인딩한다.
 *
 * <p>{@code retentionMonths}: 목록에 보이는 기간 — 발생(created_at) 후 이 개월 수가 지난 알림은 목록·MY-01 미리보기에서
 * 빠진다("오래된 알림은 자동 보관"). 데이터는 지우지 않는다(보관 테이블·파기 배치 없음, 읽음 처리는 기간과 무관).
 * MY-04 화면 안내 문구가 같은 6을 쓴다(프론트 상수) — 이 값을 바꾸면 문구도 함께 바꾼다(CLAUDE.md "SCR-MY-04" 절 D6).
 */
@ConfigurationProperties(prefix = "homesense.notification.history")
@Validated
public record NotificationHistoryProperties(@DefaultValue("6") @Min(1) int retentionMonths) {
}
