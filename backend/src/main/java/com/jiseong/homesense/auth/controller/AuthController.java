package com.jiseong.homesense.auth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jiseong.homesense.auth.dto.EmailCheckResponse;
import com.jiseong.homesense.auth.dto.LoginRequest;
import com.jiseong.homesense.auth.dto.LoginResponse;
import com.jiseong.homesense.auth.dto.RefreshRequest;
import com.jiseong.homesense.auth.dto.SignupRequest;
import com.jiseong.homesense.auth.dto.SignupResponse;
import com.jiseong.homesense.auth.dto.TokenResponse;
import com.jiseong.homesense.auth.service.AuthService;
import com.jiseong.homesense.common.response.ApiResponse;
import com.jiseong.homesense.common.security.UserPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** API-AUTH-01. base path: /api/auth. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request.toCommand()));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request.toCommand()));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refreshAccessToken(request.refreshToken()));
    }

    /** 인증 필요 — SecurityConfig가 POST /api/auth/logout에 authenticated()를 강제한다. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody RefreshRequest request) {
        authService.logout(me.userId(), request.refreshToken());
        return ApiResponse.success((Void) null);
    }

    @GetMapping("/check-email")
    public ApiResponse<EmailCheckResponse> checkEmail(@RequestParam String email) {
        return ApiResponse.success(new EmailCheckResponse(authService.isEmailDuplicate(email)));
    }
}
