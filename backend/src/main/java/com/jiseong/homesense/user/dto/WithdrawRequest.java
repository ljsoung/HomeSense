package com.jiseong.homesense.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * MY-01 회원 탈퇴 요청. UI정의서 MY-01 이벤트 정의(탈퇴 사유 확인 → 최종 확인 다이얼로그)에 맞춰
 * password(재확인)와 reason(탈퇴 사유)을 함께 받는다.
 *
 * <p>reason은 검증 대상이 아니고(자유 텍스트 또는 프론트 드롭다운 값 그대로 수용), 저장할 곳이 아직
 * 없어(User row는 물리 삭제되지 않고 남으므로 훗날 컬럼을 추가해 저장하는 확장은 가능) 현재는
 * {@link #toCommand()}에서 의도적으로 넘기지 않는다 — 받기만 하고 사용하지 않는다.
 */
public record WithdrawRequest(@NotBlank String password, String reason) {

    public WithdrawCommand toCommand() {
        return new WithdrawCommand(password);
    }
}
