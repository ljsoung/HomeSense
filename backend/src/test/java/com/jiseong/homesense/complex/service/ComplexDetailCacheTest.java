package com.jiseong.homesense.complex.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.complex.dto.ComplexDetailResponse;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.exception.ComplexNotFoundException;
import com.jiseong.homesense.complex.repository.ComplexRepository;

@ExtendWith(MockitoExtension.class)
class ComplexDetailCacheTest {

    @Mock
    private ComplexRepository complexRepository;

    private ComplexDetailCache complexDetailCache;

    @BeforeEach
    void setUp() {
        complexDetailCache = new ComplexDetailCache(complexRepository);
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
                .dataUpdatedAt(LocalDate.of(2026, 1, 1))
                .build();
    }

    @Test
    void get_존재하지_않으면_ComplexNotFoundException을_던진다() {
        when(complexRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> complexDetailCache.get(1L)).isInstanceOf(ComplexNotFoundException.class);
    }

    @Test
    void get_존재하면_상세정보를_반환한다() {
        when(complexRepository.findById(1L)).thenReturn(Optional.of(complex(1L)));

        ComplexDetailResponse response = complexDetailCache.get(1L);

        assertThat(response.complexId()).isEqualTo(1L);
        assertThat(response.complexName()).isEqualTo("테스트단지1");
    }

    @Test
    void get_법정동_매칭_대기면_matchPending이_true다() {
        when(complexRepository.findById(1L)).thenReturn(Optional.of(complex(1L)));

        ComplexDetailResponse response = complexDetailCache.get(1L);

        assertThat(response.matchPending()).isTrue();
    }
}
