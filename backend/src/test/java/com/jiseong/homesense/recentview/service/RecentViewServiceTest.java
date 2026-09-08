package com.jiseong.homesense.recentview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.recentview.dto.RecentViewResponse;
import com.jiseong.homesense.recentview.dto.RecentViewTarget;
import com.jiseong.homesense.recentview.entity.RecentView;
import com.jiseong.homesense.recentview.repository.RecentViewRepository;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.user.entity.User;
import com.jiseong.homesense.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RecentViewServiceTest {

    @Mock
    private RecentViewRepository recentViewRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ComplexRepository complexRepository;

    private RecentViewService recentViewService;

    @BeforeEach
    void setUp() {
        recentViewService = new RecentViewService(recentViewRepository, userRepository, complexRepository);
    }

    private static Complex complex(Long id) {
        return Complex.builder()
                .complexId(id)
                .sourceComplexCd("SRC-" + id)
                .complexName("테스트단지" + id)
                .complexType("아파트")
                .elevatorPassengerCount((short) 1)
                .elevatorCargoCount((short) 0)
                .elevatorCombinedCount((short) 0)
                .build();
    }

    @Test
    void getRecent_userId와_sessionId가_모두_없으면_빈_리스트를_반환한다() {
        List<RecentViewResponse> result = recentViewService.getRecent(null, null, 3);

        assertThat(result).isEmpty();
    }

    @Test
    void getRecent_userId가_있으면_회원_기준으로_조회한다() {
        RecentView view = RecentView.record(null, "session-x", complex(1L), HousingType.APT);
        when(recentViewRepository.findByUser_UserIdOrderByViewedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(view));

        List<RecentViewResponse> result = recentViewService.getRecent(1L, null, 3);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).complexId()).isEqualTo(1L);
    }

    @Test
    void getRecent_userId가_없으면_sessionId_기준으로_조회한다() {
        RecentView view = RecentView.record(null, "session-x", complex(2L), HousingType.VILLA);
        when(recentViewRepository.findBySessionIdOrderByViewedAtDesc(eq("session-x"), any(Pageable.class)))
                .thenReturn(List.of(view));

        List<RecentViewResponse> result = recentViewService.getRecent(null, "session-x", 3);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).complexId()).isEqualTo(2L);
    }

    @Test
    void record_userId와_sessionId가_모두_없으면_조용히_반환한다() {
        recentViewService.record(null, null, new RecentViewTarget(1L, HousingType.APT));

        verify(recentViewRepository, never()).save(any());
    }

    /**
     * complex_type 원본 미기재(NULL, 약 0.48%)인 단지는 Complex.inferHousingType()이 null을
     * 돌려준다 — recent_view.housing_type이 NOT NULL이라 이 경우 잘못된 값을 추정해 저장하는
     * 대신 조용히 기록을 스킵한다(CLAUDE.md SVC-RCV-01 절 참고).
     */
    @Test
    void record_target의_housingType이_null이면_조용히_반환한다() {
        recentViewService.record(1L, null, new RecentViewTarget(10L, null));

        verify(recentViewRepository, never()).save(any());
        verify(recentViewRepository, never()).findByUser_UserIdAndComplex_ComplexId(any(), any());
    }

    @Test
    void record_기존_레코드가_있으면_viewedAt만_갱신하고_신규_저장하지_않는다() {
        RecentView existing = RecentView.record(null, "session-x", complex(1L), HousingType.APT);
        LocalDateTime originalViewedAt = existing.getViewedAt();
        when(recentViewRepository.findBySessionIdAndComplex_ComplexId("session-x", 1L))
                .thenReturn(Optional.of(existing));

        recentViewService.record(null, "session-x", new RecentViewTarget(1L, HousingType.APT));

        verify(recentViewRepository, never()).save(any());
        assertThat(existing.getViewedAt()).isAfterOrEqualTo(originalViewedAt);
    }

    @Test
    void record_기존_레코드가_없으면_신규로_저장한다() {
        when(recentViewRepository.findByUser_UserIdAndComplex_ComplexId(1L, 10L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().build());
        when(complexRepository.getReferenceById(10L)).thenReturn(complex(10L));
        when(recentViewRepository.countByUser_UserId(1L)).thenReturn(1L);

        recentViewService.record(1L, null, new RecentViewTarget(10L, HousingType.APT));

        ArgumentCaptor<RecentView> captor = ArgumentCaptor.forClass(RecentView.class);
        verify(recentViewRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getComplex().getComplexId()).isEqualTo(10L);
        assertThat(captor.getValue().getHousingType()).isEqualTo(HousingType.APT);
    }

    @Test
    void record_보관건수_상한을_넘으면_가장_오래된_레코드부터_삭제한다() {
        when(recentViewRepository.findBySessionIdAndComplex_ComplexId("session-x", 10L)).thenReturn(Optional.empty());
        when(complexRepository.getReferenceById(10L)).thenReturn(complex(10L));
        when(recentViewRepository.countBySessionId("session-x")).thenReturn(21L);
        RecentView oldest = RecentView.record(null, "session-x", complex(999L), HousingType.APT);
        when(recentViewRepository.findBySessionIdOrderByViewedAtAsc(eq("session-x"), any(Pageable.class)))
                .thenReturn(List.of(oldest));

        recentViewService.record(null, "session-x", new RecentViewTarget(10L, HousingType.APT));

        verify(recentViewRepository).deleteAll(List.of(oldest));
    }

    @Test
    void record_보관건수가_상한_이하면_삭제하지_않는다() {
        when(recentViewRepository.findByUser_UserIdAndComplex_ComplexId(1L, 10L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().build());
        when(complexRepository.getReferenceById(10L)).thenReturn(complex(10L));
        when(recentViewRepository.countByUser_UserId(anyLong())).thenReturn(5L);

        recentViewService.record(1L, null, new RecentViewTarget(10L, HousingType.APT));

        verify(recentViewRepository, never()).deleteAll(any());
    }
}
