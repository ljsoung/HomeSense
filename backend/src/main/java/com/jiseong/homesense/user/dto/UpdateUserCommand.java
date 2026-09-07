package com.jiseong.homesense.user.dto;

/** SVC-USER-01.updateUser() 입력. 세 필드 모두 null이면 해당 항목을 변경하지 않는다. */
public record UpdateUserCommand(String nickname, String currentPassword, String newPassword) {
}
