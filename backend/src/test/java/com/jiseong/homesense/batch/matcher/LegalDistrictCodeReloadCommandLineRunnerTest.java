package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import com.jiseong.homesense.region.service.RegionCodePrefixResolver;
import com.jiseong.homesense.region.service.RegionCodePrefixResolver.VersionPublication;

class LegalDistrictCodeReloadCommandLineRunnerTest {

    private final LegalDistrictCodeLoader loader = mock(LegalDistrictCodeLoader.class);
    private final RegionCodePrefixResolver resolver = mock(RegionCodePrefixResolver.class);
    private final LegalDistrictCodeReloadCommandLineRunner runner =
            new LegalDistrictCodeReloadCommandLineRunner(loader, resolver, mock(ApplicationContext.class));

    @Test
    void 재적재가_성공하면_종료_코드_0() {
        when(resolver.lastVersionPublication())
                .thenReturn(Optional.of(new VersionPublication(true, "v1", null)));

        assertThat(runner.execute()).isZero();
        verify(loader).loadInitial(any(File.class));
    }

    @Test
    void 버전_발행이_실패해도_재적재가_성공했으면_종료_코드_0() {
        when(resolver.lastVersionPublication())
                .thenReturn(Optional.of(new VersionPublication(false, null, "down")));

        assertThat(runner.execute()).isZero();
    }

    @Test
    void 재적재가_실패하면_종료_코드_1() {
        doThrow(new IllegalStateException("boom")).when(loader).loadInitial(any(File.class));

        assertThat(runner.execute()).isEqualTo(1);
    }
}
