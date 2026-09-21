package com.jiseong.homesense.auth.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;
import com.jiseong.homesense.user.entity.UserStatus;

/**
 * SVC-AUTH-01.login()/reactivate()/refreshAccessToken() — status가 WITHDRAWN 또는 SUSPENDED인 계정.
 * HTTP 403은 그대로 두고 errorCode만 {@code ACCOUNT_WITHDRAWN}/{@code ACCOUNT_SUSPENDED}로 구분한다.
 *
 * <p>이 예외는 <b>비밀번호 검증을 통과한 뒤에만</b> 던진다 — 비밀번호를 모르는 사람에게 계정의 탈퇴·정지
 * 상태를 알려주지 않기 위해서다(AUTH-01 "계정 존재 여부 비노출" 원칙). 프론트가 {@code error.message}를 그대로
 * 표시하므로 탈퇴 문구에 "철회할 수 있습니다"는 넣지 않는다 — 철회 UI가 아직 없어 그렇게 쓰면 허위 안내가 된다.
 */
public class AccountNotActiveException extends BusinessException {

    public AccountNotActiveException(UserStatus status) {
        super(errorCodeOf(status), messageOf(status), HttpStatus.FORBIDDEN);
    }

    private static String errorCodeOf(UserStatus status) {
        return switch (status) {
            case WITHDRAWN -> "ACCOUNT_WITHDRAWN";
            case SUSPENDED -> "ACCOUNT_SUSPENDED";
            case ACTIVE -> throw new IllegalArgumentException("ACTIVE 계정에는 AccountNotActiveException을 쓰지 않는다");
        };
    }

    private static String messageOf(UserStatus status) {
        return switch (status) {
            case WITHDRAWN -> "탈퇴 처리된 계정입니다.";
            case SUSPENDED -> "탈퇴하거나 정지된 계정입니다";
            case ACTIVE -> throw new IllegalArgumentException("ACTIVE 계정에는 AccountNotActiveException을 쓰지 않는다");
        };
    }
}
