package com.jiseong.homesense.favorite.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-FAV-01.removeFavoriteProperty()/removeFavoriteRegion() — 대상 레코드는 존재하지만 요청자
 * userId와 소유자(user_id)가 다른 경우. Spring Security의
 * {@code org.springframework.security.access.AccessDeniedException}과 이름이 같지만 이 프로젝트의
 * BusinessException 계층에 속하는 별개 클래스다 — GlobalExceptionHandler가 COM-EXC-01 단일 지점에서
 * 처리하도록 이 패키지 안에서만 이 이름으로 import해 쓴다(SVC-AUTH-01.logout()의 owner 검증과 같은
 * 패턴, CLAUDE.md SVC-AUTH-01 절 참고 — 다만 그쪽은 InvalidRefreshTokenException을 재사용했고 FAV
 * 설계서는 이 상황을 위한 이름을 명시적으로 지정해 새 클래스를 둔다).
 */
public class AccessDeniedException extends BusinessException {

    public AccessDeniedException() {
        super("ACCESS_DENIED", "본인의 관심 등록만 삭제할 수 있습니다", HttpStatus.FORBIDDEN);
    }
}
