package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.common.logging.AuditLogger;
import com.jiseong.homesense.complex.entity.Complex;
import com.jiseong.homesense.complex.repository.ComplexRepository;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.MatchMethod;

@ExtendWith(MockitoExtension.class)
class ComplexMasterMatcherTest {

    private static final LegalDistrictCode YEOKSAM_DONG =
            legalDistrictCode("서울특별시", "강남구", "역삼동");

    @Mock
    private ComplexRepository complexRepository;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private ComplexMasterMatcher matcher;

    /**
     * legal_dong_address는 실제로는 "시도 시군구 동리 지번 단지명" 형태다(지번 뒤에 단지명이 그대로
     * 이어 붙는다, 실 DB 데이터로 확인) — dongRi를 반드시 함께 넘겨 이 형태를 재현한다.
     */
    private static Complex complex(Long id, String name, String dongRi, String legalDongAddress) {
        return Complex.builder()
                .complexId(id)
                .complexName(name)
                .dongRi(dongRi)
                .legalDongAddress(legalDongAddress)
                .build();
    }

    private static LegalDistrictCode legalDistrictCode(String sido, String sigungu, String dongRi) {
        return LegalDistrictCode.builder()
                .sidoName(sido)
                .sigunguName(sigungu)
                .eupmyeondongName(dongRi)
                .build();
    }

    /**
     * jibun·buildingName만 테스트별로 바꾸고 나머지는 BAT-MAT-02 매칭 로직과 무관한 값으로 채운다.
     */
    private static TradeDraft draft(String jibun, String buildingName) {
        return new TradeDraft(
                HousingType.APT,
                DealCategory.SALE,
                null,
                "15126468",
                "11680",
                "역삼동",
                buildingName,
                jibun,
                new BigDecimal("84.99"),
                (short) 10,
                (short) 2005,
                LocalDate.of(2024, 1, 15),
                120000L,
                null,
                null,
                null,
                "AGENT",
                "강남구",
                null,
                null,
                null,
                null,
                false,
                null,
                null, // complexId — matchComplex는 draft가 아니라 반환값으로 결정하므로 무관
                null,
                null,
                null);
    }

    @Test
    void 지번과_단지명이_모두_일치하면_EXACT_1점을_반환한다() {
        Complex candidate = complex(1L, "역삼래미안", "역삼동", "서울특별시 강남구 역삼동 123-4 역삼래미안");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"), YEOKSAM_DONG);

        assertThat(result.complexId()).isEqualTo(1L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
        assertThat(result.matchConfidence()).isEqualByComparingTo(new BigDecimal("1.000"));
    }

    @Test
    void 지번은_일치하지만_단지명이_다르면_EXACT를_유지하되_신뢰도를_낮춘다() {
        Complex candidate = complex(2L, "개명후아파트", "역삼동", "서울특별시 강남구 역삼동 123-4 개명후아파트");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "개명전아파트"), YEOKSAM_DONG);

        assertThat(result.complexId()).isEqualTo(2L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
        assertThat(result.matchConfidence()).isEqualByComparingTo(new BigDecimal("0.800"));
    }

    @Test
    void 산번지와_일반지번은_다른_필지로_취급한다() {
        Complex candidate = complex(3L, "역삼래미안", "역삼동", "서울특별시 강남구 역삼동 산 123-4 역삼래미안");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"), YEOKSAM_DONG);

        // 지번이 일치하지 않으므로 SIMILAR 경로로 넘어가고, 단지명이 완전히 같아 유사도 1.0 -> 최대 신뢰도로 채택된다.
        assertThat(result.complexId()).isEqualTo(3L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.SIMILAR);
        assertThat(result.matchConfidence()).isEqualByComparingTo(new BigDecimal("0.850"));
    }

    @Test
    void legal_dong_address가_지번_뒤에_단지명이_붙은_실제_형태여도_지번을_추출해_EXACT로_매칭한다() {
        // 회귀 테스트: 예전 구현은 legal_dong_address가 "시도 시군구 동리 지번"으로 끝난다고 가정해
        // 문자열 끝($)에 anchor한 정규식을 썼는데, 실제 데이터는 지번 뒤에 단지명이 그대로 붙어
        // 있어(예: "...역삼동 산 45-6 역삼래미안") 그 anchor가 단 한 건도 매치되지 않아 EXACT 경로가
        // 전국적으로 항상 무너졌다(match_method='EXACT' 0건, 실 DB로 확인).
        Complex candidate = complex(6L, "역삼래미안", "역삼동", "서울특별시 강남구 역삼동 산 45-6 역삼래미안");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("산 45-6", "역삼래미안"), YEOKSAM_DONG);

        assertThat(result.complexId()).isEqualTo(6L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
        assertThat(result.matchConfidence()).isEqualByComparingTo(new BigDecimal("1.000"));
    }

    @Test
    void 실제_단지_종로청계힐스테이트_사례로_EXACT_매칭을_검증한다() {
        // 실 DB 사례(complex_id=19): legal_dong_address="서울특별시 종로구 숭인동 766 종로청계힐스테이트",
        // 대응 실거래 jibun="766" — 지번이 완전히 일치하는데도 수정 전 코드는 SIMILAR(0.850)로
        // 오분류했다. 이 사례를 그대로 픽스처로 삼아 수정 후 EXACT/1.000이 나오는지 확인한다.
        LegalDistrictCode sunginDong = legalDistrictCode("서울특별시", "종로구", "숭인동");
        Complex candidate = complex(19L, "종로청계힐스테이트", "숭인동", "서울특별시 종로구 숭인동 766 종로청계힐스테이트");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("766", "종로청계힐스테이트"), sunginDong);

        assertThat(result.complexId()).isEqualTo(19L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
        assertThat(result.matchConfidence()).isEqualByComparingTo(new BigDecimal("1.000"));
    }

    @Test
    void 동리_뒤에_여러_지번이_콤마로_나열돼도_첫_번째_지번으로_추출한다() {
        // 실 DB 사례(complex_id=13139): 한 단지가 필지 두 곳에 걸쳐 있어 legal_dong_address에
        // "...조남동 171-2,경기도 시흥시 조남동 171-21 조남동 가야아파트"처럼 지번이 두 번 나온다.
        // 시작 앵커 추출은 콤마에서 멈춰 첫 지번(171-2)만 취한다 — 매칭 실패로 치지 않는 관대한
        // 폴백으로, 조회 API의 기존 철학(SVC-CPX-01/TRD-01의 관대한 폴백)과 일치한다.
        LegalDistrictCode jonamDong = legalDistrictCode("경기도", "시흥시", "조남동");
        Complex candidate = complex(13139L, "조남동가야아파트", "조남동",
                "경기도 시흥시 조남동 171-2,경기도 시흥시 조남동 171-21 조남동 가야아파트");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("171-2", "조남동가야아파트"), jonamDong);

        assertThat(result.complexId()).isEqualTo(13139L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
    }

    @Test
    void 블록로트_표기_지번은_지번_매칭_대상에서_제외되고_단지명_유사도로만_판정한다() {
        // 실 DB에 trade.jibun이 "가-238"/"BL-91-2" 같은 블록-로트 표기(신도시 택지지구)로 0.46% 존재한다.
        // complex.legal_dong_address에는 이런 표기가 없어(표본 확인) 지번 매칭이 원천적으로 불가능하다 —
        // 버그가 아니라 FR-2.5(98.9% 목표, 100% 아님)가 전제하는 알려진 한계다. jibun 필터가 조용히
        // 빈 결과로 떨어져 SIMILAR/미매칭 경로로 넘어가는지 확인한다.
        Complex candidate = complex(7L, "역삼래미안", "역삼동", "서울특별시 강남구 역삼동 123-4 역삼래미안");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("가-238", "역삼래미안"), YEOKSAM_DONG);

        assertThat(result.matchMethod()).isNotEqualTo(MatchMethod.EXACT);
    }

    @Test
    void 지번이_다르지만_단지명_유사도가_높으면_SIMILAR로_채택한다() {
        Complex candidate = complex(4L, "역삼래미안1차", "역삼동", "서울특별시 강남구 역삼동 999-9 역삼래미안1차");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"), YEOKSAM_DONG);

        assertThat(result.complexId()).isEqualTo(4L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.SIMILAR);
        assertThat(result.matchConfidence()).isGreaterThanOrEqualTo(new BigDecimal("0.600"));
        assertThat(result.matchConfidence()).isLessThan(new BigDecimal("0.850"));
    }

    @Test
    void 지번도_단지명도_불일치하면_매칭_실패로_처리한다() {
        Complex candidate = complex(5L, "전혀다른이름아파트", "역삼동", "서울특별시 강남구 역삼동 999-9 전혀다른이름아파트");
        when(complexRepository.findBySidoAndSigunguAndDongRi(any(), any(), any()))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"), YEOKSAM_DONG);

        assertThat(result.complexId()).isNull();
        assertThat(result.matchMethod()).isNull();
        assertThat(result.matchConfidence()).isNull();
    }

    @Test
    void 시군구_표기가_시_구_구조로_달라도_정규화해_후보를_조회한다() {
        // 실 DB 사례: complex.sigungu(xlsx)="수원장안구"(공백 없음·"시" 생략) vs
        // legal_district_code.sigungu_name(CSV)="수원시 장안구"(공백 있음·"시" 유지) — 정규화 없이는
        // findBySidoAndSigunguAndDongRi가 "수원시 장안구"를 그대로 넘겨 항상 후보 0건이 된다.
        LegalDistrictCode suwonJangan = legalDistrictCode("경기도", "수원시 장안구", "정자동");
        Complex candidate = complex(8L, "수원아이파크", "정자동", "경기도 수원장안구 정자동 100 수원아이파크");
        when(complexRepository.findBySidoAndSigunguAndDongRi(eq("경기도"), eq("수원장안구"), eq("정자동")))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("100", "수원아이파크"), suwonJangan);

        assertThat(result.complexId()).isEqualTo(8L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
    }

    @Test
    void 단일_시군_표기는_시를_보존한다() {
        // "목포시"처럼 "시"가 문자열 마지막 글자면 정규화 후에도 그대로 유지돼야 한다
        // (구가 없는 단일 시/군은 원래도 표기가 일치하므로 잘못 건드리면 안 된다).
        LegalDistrictCode mokpo = legalDistrictCode("전라남도", "목포시", "산정동");
        Complex candidate = complex(9L, "목포한라비발디", "산정동", "전라남도 목포시 산정동 200 목포한라비발디");
        when(complexRepository.findBySidoAndSigunguAndDongRi(eq("전라남도"), eq("목포시"), eq("산정동")))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("200", "목포한라비발디"), mokpo);

        assertThat(result.complexId()).isEqualTo(9L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
    }

    @Test
    void 도시_이름_자체가_시로_시작하는_단일_시군은_시를_지우지_않는다() {
        // 실 DB 사례(2026-09-16 회귀 발견): "시흥시"는 공백이 없는 단일 시/군 표기인데, 예전 구현은
        // "문자열 끝이 아닌 위치의 시"를 공백 유무와 무관하게 지워 "시흥시"의 첫 글자("시")까지
        // "시+구" 분리자로 오인해 지워버렸다 — 결과 "흥시"는 complex.sigungu="시흥시"(xlsx 원본)와
        // 영원히 달라져 시흥시 소속 거래 전체(실측 2,189건)가 1차 필터링 후보 0건으로 떨어졌었다.
        LegalDistrictCode siheung = legalDistrictCode("경기도", "시흥시", "정왕동");
        Complex candidate = complex(13L, "서해2단지", "정왕동", "경기도 시흥시 정왕동 1886-4 서해2단지");
        when(complexRepository.findBySidoAndSigunguAndDongRi(eq("경기도"), eq("시흥시"), eq("정왕동")))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("1886-4", "서해2단지"), siheung);

        assertThat(result.complexId()).isEqualTo(13L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
    }

    @Test
    void 광역시_구_표기는_영향받지_않는다() {
        // "종로구"처럼 애초에 "시"가 없는 광역시 구 표기는 정규화 규칙이 아무 변화도 주지 않아야 한다.
        Complex candidate = complex(10L, "경희궁의아침3단지", "내수동", "서울특별시 종로구 내수동 72 경희궁의아침3단지");
        when(complexRepository.findBySidoAndSigunguAndDongRi(eq("서울특별시"), eq("종로구"), eq("내수동")))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(
                draft("72", "경희궁의아침3단지"), legalDistrictCode("서울특별시", "종로구", "내수동"));

        assertThat(result.complexId()).isEqualTo(10L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
    }

    @Test
    void 리_단위_지역은_읍면_접두어를_제거한_리_이름으로_후보를_조회한다() {
        // 실 DB 사례: legal_district_code.eupmyeondong_name="팽성읍 송화리"(읍+리 결합형) vs
        // complex.dong_ri="송화리"(리 이름만, 공백 없음) — 접두어 제거 없이는 findBySidoAndSigunguAndDongRi가
        // "팽성읍 송화리"를 그대로 넘겨 항상 후보 0건이 된다(post-fix 미매칭 366건 중 354건이 이 케이스).
        LegalDistrictCode paengseongSonghwa = legalDistrictCode("경기도", "평택시", "팽성읍 송화리");
        Complex candidate = complex(11L, "송화아파트", "송화리", "경기도 평택시 송화리 65 송화아파트");
        when(complexRepository.findBySidoAndSigunguAndDongRi(eq("경기도"), eq("평택시"), eq("송화리")))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("65", "송화아파트"), paengseongSonghwa);

        assertThat(result.complexId()).isEqualTo(11L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
    }

    @Test
    void 읍면_결합없는_단일레벨_동리는_영향받지_않는다() {
        // 공백이 없는 일반 동/읍/면 표기는 extractComparableDongRi가 그대로 통과시켜야 한다
        // (이미 위 여러 테스트가 이 경로를 쓰고 있지만, 회귀 방지를 위해 명시적으로 한 번 더 확인).
        Complex candidate = complex(12L, "역삼래미안", "역삼동", "서울특별시 강남구 역삼동 123-4 역삼래미안");
        when(complexRepository.findBySidoAndSigunguAndDongRi(eq("서울특별시"), eq("강남구"), eq("역삼동")))
                .thenReturn(List.of(candidate));

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"), YEOKSAM_DONG);

        assertThat(result.complexId()).isEqualTo(12L);
        assertThat(result.matchMethod()).isEqualTo(MatchMethod.EXACT);
    }

    @Test
    void 후보가_0건이면_예외없이_매칭_실패로_처리한다() {
        when(complexRepository.findBySidoAndSigunguAndDongRi(eq("서울특별시"), eq("강남구"), eq("역삼동")))
                .thenReturn(List.of());

        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"), YEOKSAM_DONG);

        assertThat(result.complexId()).isNull();
        assertThat(result.matchMethod()).isNull();
        assertThat(result.matchConfidence()).isNull();
    }

    @Test
    void 법정동_매핑에_실패해_legalDistrictCode가_없으면_매칭_실패로_처리한다() {
        MatchResult result = matcher.matchComplex(draft("123-4", "역삼래미안"), null);

        assertThat(result.complexId()).isNull();
        assertThat(result.matchMethod()).isNull();
        assertThat(result.matchConfidence()).isNull();
    }
}
