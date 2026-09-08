package com.jiseong.homesense.common.exception;

import org.springframework.http.HttpStatus;

/**
 * SVC-CPX-01.getDetail() / SVC-FAV-01.addFavoriteProperty()가 공유한다 — 원래는 complex.exception
 * 소속이었지만 SVC-FAV-01이 요청받은 complexId로 단지를 조회할 때도 같은 "존재하지 않는 단지"
 * 오류가 필요해, CPX 전용이 아니라 여러 도메인이 함께 쓰는 COM-EXC-01 공통 예외로 옮겼다
 * (InvalidCredentialsException이 auth.exception에서 이곳으로 옮겨온 것과 같은 이유·같은 절차).
 */
public class ComplexNotFoundException extends BusinessException {

    public ComplexNotFoundException() {
        super("COMPLEX_NOT_FOUND", "존재하지 않는 단지입니다", HttpStatus.NOT_FOUND);
    }
}
