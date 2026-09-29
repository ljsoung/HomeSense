package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.auth.repository.RefreshTokenRepository;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.common.security.AccessTokenEpochService;

@ExtendWith(MockitoExtension.class)
class RefreshTokenReuseHandlerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuditLogger auditLogger;
    @Mock
    private AccessTokenEpochService accessTokenEpochService;

    @InjectMocks
    private RefreshTokenReuseHandler handler;

    @Test
    void 해당_사용자의_모든_Refresh_Token을_폐기하고_감사_로그를_남긴다() {
        handler.handle(1L);

        verify(refreshTokenRepository).revokeAllByUserId(1L);
        verify(auditLogger).logRefreshTokenReuseDetected(1L);
    }

    @Test
    void Refresh_Token_폐기_뒤_탐지_시각을_컷오프로_이미_발급된_Access_Token도_무효화한다() {
        Instant before = Instant.now();

        handler.handle(1L);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        InOrder order = inOrder(refreshTokenRepository, accessTokenEpochService);
        order.verify(refreshTokenRepository).revokeAllByUserId(1L);
        order.verify(accessTokenEpochService).invalidateTokensIssuedBefore(eq(1L), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, Instant.now());
    }
}
