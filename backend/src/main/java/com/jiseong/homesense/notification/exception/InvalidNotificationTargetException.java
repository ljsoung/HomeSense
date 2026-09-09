package com.jiseong.homesense.notification.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-NTF-01.updateSettings() — favoritePropertyId/favoriteRegionId가 동시에 지정된 경우
 * (ck_ntf_setting_target과 동일 사상 — 정확히 하나만 허용). 둘 다 비어있는 경우는
 * {@link MissingTargetException}으로 별도 구분한다.
 */
public class InvalidNotificationTargetException extends BusinessException {

    public InvalidNotificationTargetException() {
        super("INVALID_NOTIFICATION_TARGET", "favoritePropertyId와 favoriteRegionId 중 하나만 지정해야 합니다",
                HttpStatus.BAD_REQUEST);
    }
}
