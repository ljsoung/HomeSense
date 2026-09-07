package com.jiseong.homesense.user.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jiseong.homesense.common.response.ApiResponse;
import com.jiseong.homesense.common.security.UserPrincipal;
import com.jiseong.homesense.user.dto.UpdateUserRequest;
import com.jiseong.homesense.user.dto.UserResponse;
import com.jiseong.homesense.user.dto.WithdrawRequest;
import com.jiseong.homesense.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** API-USER-01. base path: /api/users. 세 엔드포인트 모두 인증 필수(SecurityConfig). */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.success(userService.getUser(me.userId()));
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMe(@AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success(userService.updateUser(me.userId(), request.toCommand()));
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody WithdrawRequest request) {
        userService.withdraw(me.userId(), request.toCommand());
        return ApiResponse.success((Void) null);
    }
}
