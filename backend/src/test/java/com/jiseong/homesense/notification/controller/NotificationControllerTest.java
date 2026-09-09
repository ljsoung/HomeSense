package com.jiseong.homesense.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.JwtTokenProvider;
import com.jiseong.homesense.common.security.UserPrincipal;
import com.jiseong.homesense.notification.dto.NotificationResponse;
import com.jiseong.homesense.notification.dto.NotificationSettingResponse;
import com.jiseong.homesense.notification.dto.UpdateNotificationSettingsCommand;
import com.jiseong.homesense.notification.entity.NotificationType;
import com.jiseong.homesense.notification.exception.AccessDeniedException;
import com.jiseong.homesense.notification.exception.InvalidNotificationTargetException;
import com.jiseong.homesense.notification.exception.NotificationNotFoundException;
import com.jiseong.homesense.notification.service.NotificationService;

/*
 * addFilters=false라 SecurityConfig의 authenticated() 규칙 자체는 검증하지 않는다(CLAUDE.md
 * 트러블슈팅 노트, FavoriteControllerTest와 같은 접근) — @AuthenticationPrincipal이 Controller까지
 * userId를 정확히 전달하는지, 그리고 Service가 던지는 예외를 COM-RES-01 포맷으로 옮기는지만 확인한다.
 */
@WebMvcTest(controllers = NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private AuditLogger auditLogger;

    private static final UserPrincipal ME = new UserPrincipal(1L, "USER");

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ME, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 알림설정_목록_조회는_인증된_사용자ID로_조회한다() throws Exception {
        when(notificationService.getSettings(1L)).thenReturn(List.of(
                new NotificationSettingResponse(1L, 100L, null, new BigDecimal("5.0"), true, false)));

        mockMvc.perform(get("/api/notifications/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].favoritePropertyId").value(100));
    }

    @Test
    void 알림설정_수정에_성공하면_200을_반환한다() throws Exception {
        mockMvc.perform(put("/api/notifications/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favoritePropertyId\":100,\"priceChangeThresholdPct\":5.0,"
                                + "\"newTradeAlertYn\":true,\"emailAlertYn\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).updateSettings(eq(1L),
                eq(new UpdateNotificationSettingsCommand(100L, null, new BigDecimal("5.0"), true, false)));
    }

    @Test
    void 알림설정_수정시_두_대상이_모두_지정되면_400을_반환한다() throws Exception {
        doThrow(new InvalidNotificationTargetException()).when(notificationService).updateSettings(eq(1L), any());

        mockMvc.perform(put("/api/notifications/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favoritePropertyId\":100,\"favoriteRegionId\":200,"
                                + "\"priceChangeThresholdPct\":5.0,\"newTradeAlertYn\":true,\"emailAlertYn\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_NOTIFICATION_TARGET"));
    }

    @Test
    void 알림설정_수정시_임계치가_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(put("/api/notifications/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favoritePropertyId\":100,\"priceChangeThresholdPct\":150,"
                                + "\"newTradeAlertYn\":true,\"emailAlertYn\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 알림설정_수정시_임계치가_소수_둘째자리를_가지면_400을_반환한다() throws Exception {
        mockMvc.perform(put("/api/notifications/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favoritePropertyId\":100,\"priceChangeThresholdPct\":0.04,"
                                + "\"newTradeAlertYn\":true,\"emailAlertYn\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void 알림이력_조회는_type_파라미터를_그대로_전달한다() throws Exception {
        when(notificationService.getNotifications(eq(1L), eq(NotificationType.NEW_TRADE), any()))
                .thenReturn(new PageImpl<>(List.of(new NotificationResponse(
                        1L, NotificationType.NEW_TRADE, "제목", "내용", 10L, null, 20L, false,
                        LocalDateTime.now())), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/notifications").param("type", "NEW_TRADE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].notificationId").value(1))
                .andExpect(jsonPath("$.pageMeta.totalElements").value(1));
    }

    @Test
    void 알림이력_조회시_type이_없으면_필터없이_조회한다() throws Exception {
        when(notificationService.getNotifications(eq(1L), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 알림_읽음처리에_성공하면_인증된_사용자ID로_서비스를_호출한다() throws Exception {
        mockMvc.perform(patch("/api/notifications/{id}/read", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).markAsRead(eq(1L), eq(1L));
    }

    @Test
    void 존재하지_않는_알림을_읽음처리하면_404를_반환한다() throws Exception {
        doThrow(new NotificationNotFoundException()).when(notificationService).markAsRead(1L, 999L);

        mockMvc.perform(patch("/api/notifications/{id}/read", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void 타인의_알림을_읽음처리하면_403을_반환한다() throws Exception {
        doThrow(new AccessDeniedException()).when(notificationService).markAsRead(1L, 1L);

        mockMvc.perform(patch("/api/notifications/{id}/read", 1L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }
}
