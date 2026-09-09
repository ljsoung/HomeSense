package com.jiseong.homesense.notification.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-NTF-01.markAsRead() — 존재하지 않는 notificationId로 읽음 처리를 시도한 경우. 설계서
 * 예외표엔 이 케이스가 명시돼 있지 않다(AccessDeniedException은 "존재하지만 소유자가 다름"만
 * 다룬다) — FavoriteNotFoundException/AccessDeniedException을 나눠 쓰는 SVC-FAV-01의 remove*()와
 * 같은 구조로 분리했다.
 */
public class NotificationNotFoundException extends BusinessException {

    public NotificationNotFoundException() {
        super("NOTIFICATION_NOT_FOUND", "존재하지 않는 알림입니다", HttpStatus.NOT_FOUND);
    }
}
