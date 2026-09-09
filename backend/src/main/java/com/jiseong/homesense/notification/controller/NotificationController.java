package com.jiseong.homesense.notification.controller;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jiseong.homesense.common.response.ApiResponse;
import com.jiseong.homesense.common.security.UserPrincipal;
import com.jiseong.homesense.notification.dto.NotificationResponse;
import com.jiseong.homesense.notification.dto.NotificationSettingResponse;
import com.jiseong.homesense.notification.dto.UpdateNotificationSettingsRequest;
import com.jiseong.homesense.notification.entity.NotificationType;
import com.jiseong.homesense.notification.service.NotificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * API-NTF-01. base path: /api/notifications. 전 엔드포인트 인증 필수(SecurityConfig, FAV/USER와
 * 같은 패턴 — 회원별 개인화 데이터).
 *
 * <p>getNotifications()의 실제 반환 타입은 설계서 표의 {@code ApiResponse<PageResponse<T>>}와
 * 다르다 — 프로젝트 어디에도 PageResponse 클래스가 없어, COM-RES-01이 이미 정의한
 * {@code ApiResponse.success(Page<T>)}(data+pageMeta) 관례를 CPX/TRD와 동일하게 재사용한다
 * (CLAUDE.md SVC-CPX-01 절 "GET /search·/map 응답 타입" 참고 — 이 프로젝트에서 이미 확정된 관례).
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/settings")
    public ApiResponse<List<NotificationSettingResponse>> getSettings(@AuthenticationPrincipal UserPrincipal me) {
        return ApiResponse.success(notificationService.getSettings(me.userId()));
    }

    @PutMapping("/settings")
    public ApiResponse<Void> updateSettings(
            @AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody UpdateNotificationSettingsRequest request) {
        notificationService.updateSettings(me.userId(), request.toCommand());
        return ApiResponse.success((Void) null);
    }

    /**
     * MY-04 알림 이력(전체/가격변동/신규거래 필터). type이 비어있거나 허용 목록(PRICE_CHANGE/NEW_TRADE)
     * 밖이면 필터를 걸지 않고 전체를 반환한다 — TradeController.getHistory()의 housingType/dealType
     * 파라미터와 같은 이유(설계서 예외표가 이 파라미터의 검증 실패를 별도 예외로 다루지 않는다).
     */
    @GetMapping
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) String type,
            Pageable pageable) {
        return ApiResponse.success(notificationService.getNotifications(me.userId(), parseType(type), pageable));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        notificationService.markAsRead(me.userId(), id);
        return ApiResponse.success((Void) null);
    }

    private NotificationType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return NotificationType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
