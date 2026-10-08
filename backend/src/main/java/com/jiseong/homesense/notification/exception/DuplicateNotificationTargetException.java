package com.jiseong.homesense.notification.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-NTF-01.updateSettings() — 한 요청 안에 같은 대상(같은 favoritePropertyId 또는 favoriteRegionId)이 두 번 이상 들어 있는 경우.
 * 어느 값으로 저장할지 정할 수 없어 요청 전체를 거부한다. MY-03은 선택 목록에서 만들므로 API를 직접 호출할 때만 발생이 예상된다.
 */
public class DuplicateNotificationTargetException extends BusinessException {

    public DuplicateNotificationTargetException() {
        super("DUPLICATE_NOTIFICATION_TARGET", "같은 알림 설정 대상이 요청에 두 번 이상 포함돼 있습니다", HttpStatus.BAD_REQUEST);
    }
}
