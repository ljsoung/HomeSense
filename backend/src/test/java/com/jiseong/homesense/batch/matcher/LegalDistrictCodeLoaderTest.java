package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.region.repository.LegalDistrictCodeRepository;

/**
 * 코드리뷰(Codex, SVC-RGN-01 PR)에서 "리(里) 단위 행은 eupmyeondongName이 실제로 무엇으로 저장되는지
 * 확인이 필요하다"는 지적을 받아 추가한 테스트다 — {@code resolveNameParts()}가 시도/시군구 대표행에만
 * 특수 분기를 두고, 그 이하(읍면동만 있는 행이든 읍면동+리가 있는 행이든)는 전부 같은 분기를 타
 * "시군구 대표행 이름을 뺀 나머지 전체"를 eupmyeondongName 하나에 그대로 담는다는 사실은 코드만
 * 봐서는 직관적이지 않다.
 *
 * <p>이 테스트가 증명하는 것: 형제 리(동부리/서부리)끼리 eupmyeondongName이 서로 다른 문자열
 * ("기장읍 동부리"/"기장읍 서부리")로 저장돼 자동완성에서 서로 구분되지 않는 완전한 중복은 생기지
 * 않는다. 다만 국토교통부 실거래가 API의 umdNm이 리 지역에서 읍/면 이름만("기장읍") 주는지, 읍+리를
 * 합친 값을 주는지는 이 프로젝트가 아직 실제 API 응답으로 확인하지 못했다 — 전자라면
 * {@code LegalDistrictMatcher.matchByTradeSggCd()}가 이런 리 행의 eupmyeondongName과 절대 일치하지
 * 않아 그 행에는 거래가 영원히 매칭되지 않는다(BAT-MAT-01 완결 필요, CLAUDE.md 참고).
 */
@ExtendWith(MockitoExtension.class)
class LegalDistrictCodeLoaderTest {

    @Mock
    private LegalDistrictCodeRepository legalDistrictCodeRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private LegalDistrictCodeLoader loader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        loader = new LegalDistrictCodeLoader(legalDistrictCodeRepository, eventPublisher);
    }

    @SuppressWarnings("unchecked")
    @Test
    void 읍_대표행은_읍이름만_리_행은_읍과_리를_합친_문자열을_eupmyeondongName으로_저장한다() throws IOException {
        File csv = writeCsv("""
                법정동코드,법정동명,폐지여부
                2600000000,부산광역시,존재
                2671000000,부산광역시 기장군,존재
                2671002500,부산광역시 기장군 기장읍,존재
                2671002501,부산광역시 기장군 기장읍 동부리,존재
                2671002502,부산광역시 기장군 기장읍 서부리,존재
                """);

        loader.loadInitial(csv);

        ArgumentCaptor<List<LegalDistrictCode>> captor = ArgumentCaptor.forClass(List.class);
        verify(legalDistrictCodeRepository).saveAll(captor.capture());
        List<LegalDistrictCode> rows = captor.getValue();

        assertThat(findByCode(rows, "2671002500").getEupmyeondongName()).isEqualTo("기장읍");
        assertThat(findByCode(rows, "2671002501").getEupmyeondongName()).isEqualTo("기장읍 동부리");
        assertThat(findByCode(rows, "2671002502").getEupmyeondongName()).isEqualTo("기장읍 서부리");
    }

    private LegalDistrictCode findByCode(List<LegalDistrictCode> rows, String code) {
        return rows.stream()
                .filter(row -> row.getLegalDongCd().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("적재된 행 중 코드 " + code + "를 찾지 못했다"));
    }

    private File writeCsv(String content) throws IOException {
        Path csvPath = tempDir.resolve("legal_district_code.csv");
        Files.write(csvPath, content.getBytes(Charset.forName("MS949")));
        return csvPath.toFile();
    }
}
