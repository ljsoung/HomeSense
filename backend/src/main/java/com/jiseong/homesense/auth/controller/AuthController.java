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
import com.jiseong.homesense.auth.dto.PasswordResetConfirmRequest;
import com.jiseong.homesense.auth.dto.PasswordResetRequest;
import com.jiseong.homesense.auth.dto.ReactivateRequest;
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

    /** 탈퇴 철회 — 탈퇴 계정은 로그인할 수 없는 인증 전 요청이라 login/signup처럼 SecurityConfig의 permitAll을 그대로 탄다. */
    @PostMapping("/reactivate")
    public ApiResponse<LoginResponse> reactivate(@Valid @RequestBody ReactivateRequest request) {
        return ApiResponse.success(authService.reactivate(request.toCommand()));
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

    /** AUTH-03 1단계 — 계정 존재 여부와 무관하게 항상 동일한 성공 응답(예외는 쿨다운뿐, 429). */
    @PostMapping("/password-reset-request")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.email());
        return ApiResponse.success((Void) null);
    }

    /** AUTH-03 2단계 사전 검증(선택 API) — 만료·미존재 토큰이면 400, 소비하지 않는다(peek). */
    @GetMapping("/password-reset/validate-token")
    public ApiResponse<Void> validatePasswordResetToken(@RequestParam String token) {
        authService.validatePasswordResetToken(token);
        return ApiResponse.success((Void) null);
    }

    /** AUTH-03 2단계 — 토큰 소비(1회용) + 비밀번호 변경 + 기존 세션 전체 폐기. */
    @PostMapping("/password-reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.success((Void) null);
    }
}
