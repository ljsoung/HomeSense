package com.jiseong.homesense.auth.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.common.logging.AuditLogger;

@ExtendWith(MockitoExtension.class)
class RefreshTokenReuseHandlerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private RefreshTokenReuseHandler handler;

    @Test
    void 해당_사용자의_모든_Refresh_Token을_폐기하고_감사_로그를_남긴다() {
        handler.handle(1L);

        verify(refreshTokenRepository).revokeAllByUserId(1L);
        verify(auditLogger).logRefreshTokenReuseDetected(1L);
    }
}
