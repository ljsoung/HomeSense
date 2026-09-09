package com.jiseong.homesense.notification.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-NTF-01.markAsRead() — 대상 notification의 소유자가 요청자와 다른 경우. Spring Security의
 * {@code org.springframework.security.access.AccessDeniedException}과 이름이 같지만 이 프로젝트의
 * BusinessException 계층에 속하는 별개 클래스다(FAV의 같은 이름 클래스와 같은 패턴, CLAUDE.md
 * SVC-FAV-01 절 참고 — 도메인마다 소유자 검증 메시지가 달라 공통으로 승격하지 않았다).
 *
 * <p>updateSettings()가 요청받은 favoritePropertyId/favoriteRegionId의 소유자를 검증할 때도
 * 재사용한다 — "본인 소유가 아닌 자원을 대상으로 처리를 시도"라는 같은 성격의 위반이라 별도
 * 클래스를 두지 않았다(설계서 예외표엔 markAsRead() 시나리오만 명시돼 있다 — 지성 확인 필요).
 */
public class AccessDeniedException extends BusinessException {

    public AccessDeniedException() {
        super("ACCESS_DENIED", "본인 소유의 자원에 대해서만 처리할 수 있습니다", HttpStatus.FORBIDDEN);
    }
}
