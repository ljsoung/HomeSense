package com.jiseong.homesense.auth.exception;

import org.springframework.http.HttpStatus;

import com.jiseong.homesense.common.exception.BusinessException;

/**
 * SVC-AUTH-01.reactivate() — 탈퇴 철회 가능 기간(유예기간)이 지났거나, 조건부 UPDATE 직전에 자동 파기가 먼저
 * 처리된 경우. 두 상황을 구분하지 않고 같은 중립적 문구를 쓴다.
 */
public class ReactivationPeriodExpiredException extends BusinessException {

    public ReactivationPeriodExpiredException() {
        super("REACTIVATION_PERIOD_EXPIRED", "탈퇴 철회 가능 기간이 지났습니다.", HttpStatus.GONE);
    }
}
