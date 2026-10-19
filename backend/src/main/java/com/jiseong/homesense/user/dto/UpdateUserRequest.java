package com.jiseong.homesense.user.dto;

import com.jiseong.homesense.common.validation.ValidNicknameIfPresent;
import com.jiseong.homesense.common.validation.ValidPasswordIfPresent;

/**
 * MY-05 회원정보 수정 요청. 닉네임/비밀번호 각각 선택적 부분 수정이라 필드를 생략(null)하면 해당
 * 항목은 건드리지 않는다 — {@link ValidNicknameIfPresent}/{@link ValidPasswordIfPresent}가 null을
 * 유효로 통과시키고 값이 있을 때만 회원가입과 동일한 정책을 적용한다.
 *
 * <p>currentPassword는 새 비밀번호 정책이 아니라 저장된 값과의 일치 여부만 확인하는 대상이라
 * 별도 형식 검증을 붙이지 않는다(LoginRequest.password와 같은 이유) — newPassword가 있는데
 * currentPassword가 비어 있으면 {@link CurrentPasswordRequired}가 400(currentPassword 필드 오류)으로 막는다.
 * 이메일 필드는 두지 않는다 — 이메일(로그인 ID) 변경은 소유 확인 플로우가 없어 범위 밖이다(D1).
 */
@CurrentPasswordRequired
public record UpdateUserRequest(
        @ValidNicknameIfPresent String nickname,
        String currentPassword,
        @ValidPasswordIfPresent String newPassword) {

    public UpdateUserCommand toCommand() {
        return new UpdateUserCommand(nickname, currentPassword, newPassword);
    }
}
