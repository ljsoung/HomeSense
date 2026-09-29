package com.jiseong.homesense.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

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
    @Mock
    private PlatformTransactionManager transactionManager;

    private RefreshTokenReuseHandler handler;
    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(transaction);
        handler = new RefreshTokenReuseHandler(
                refreshTokenRepository, auditLogger, accessTokenEpochService, transactionManager);
    }

    @Test
    void 새_트랜잭션에서_해당_사용자의_모든_Refresh_Token을_폐기하고_감사_로그를_남긴다() {
        handler.handle(1L);

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(refreshTokenRepository).revokeAllByUserId(1L);
        verify(auditLogger).logRefreshTokenReuseDetected(1L);
    }

    @Test
    void 폐기가_커밋된_뒤_탐지_시각을_컷오프로_이미_발급된_Access_Token도_무효화한다() {
        Instant before = Instant.now();

        handler.handle(1L);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        InOrder order = inOrder(refreshTokenRepository, transactionManager, accessTokenEpochService);
        order.verify(refreshTokenRepository).revokeAllByUserId(1L);
        order.verify(transactionManager).commit(transaction);
        order.verify(accessTokenEpochService).invalidateTokensIssuedBefore(eq(1L), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, Instant.now());
    }

    @Test
    void 컷오프_쓰기가_실패해도_폐기는_이미_커밋됐고_예외는_그대로_던진다() {
        // 코드리뷰 P2 — 컷오프가 폐기와 같은 트랜잭션 안에 있으면 Redis 장애가 폐기까지 롤백시켰다.
        // 예외를 삼키지 않아야 재발급 클라이언트가 다시 시도해 컷오프 쓰기를 재시도한다.
        doThrow(new RedisConnectionFailureException("down"))
                .when(accessTokenEpochService).invalidateTokensIssuedBefore(eq(1L), any());

        assertThatThrownBy(() -> handler.handle(1L)).isInstanceOf(RedisConnectionFailureException.class);

        verify(transactionManager).commit(transaction);
        verify(transactionManager, never()).rollback(any());
    }
}
