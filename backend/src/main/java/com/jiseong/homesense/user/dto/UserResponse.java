package com.jiseong.homesense.user.dto;

import java.time.LocalDateTime;

import com.jiseong.homesense.user.entity.User;

/** MY-01 내 정보 조회/수정 응답. role·status·social_provider 등 내부 필드는 노출하지 않는다. */
public record UserResponse(Long userId, String email, String nickname, LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getEmail(), user.getNickname(), user.getCreatedAt());
    }
}
