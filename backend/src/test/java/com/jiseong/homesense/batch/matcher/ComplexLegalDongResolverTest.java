package com.jiseong.homesense.batch.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.region.entity.LegalDistrictCode;

class ComplexLegalDongResolverTest {

    private static LegalDistrictCode code(String cd, String sido, String sigungu, String emd, boolean active) {
        String name = String.join(" ", java.util.stream.Stream.of(sido, sigungu, emd)
                .filter(p -> p != null).toList());
        return LegalDistrictCode.builder().legalDongCd(cd).legalDongName(name).sidoName(sido).sigunguName(sigungu)
                .eupmyeondongName(emd).isActive(active).dataVersion(LocalDate.of(2026, 9, 17)).build();
    }

    private final ComplexLegalDongResolver resolver = ComplexLegalDongResolver.of(List.of(
            code("4111100000", "경기도", "수원시 장안구", null, true),
            code("4111112900", "경기도", "수원시 장안구", "파장동", true),
            code("3611010100", "세종특별자치시", null, "반곡동", true),
            code("4617025000", "전라남도", "여수시", "돌산읍", true),
            code("4617025021", "전라남도", "여수시", "돌산읍 우두리", true),
            code("4617031000", "전라남도", "여수시", "소라면", true),
            code("4617031021", "전라남도", "여수시", "소라면 우두리", true),
            code("4159325000", "경기도", "화성시 효행구", "봉담읍", true),
            code("4159325024", "경기도", "화성시 효행구", "봉담읍 동화리", true),
            code("4159025024", "경기도", "화성시", "봉담읍 동화리", false)));

    private static String cd(Optional<LegalDistrictCode> result) {
        return result.map(LegalDistrictCode::getLegalDongCd).orElse(null);
    }

    @Test
    void 시_구_도시는_붙여쓴_complex_시군구로도_매칭된다() {
        assertThat(cd(resolver.byName("경기도", "수원장안구", "파장동", "경기도 수원장안구 파장동 199 궁전아파트")))
                .isEqualTo("4111112900");
        assertThat(cd(resolver.byAddressPrefix("경기도 수원장안구 파장동 199 궁전아파트"))).isEqualTo("4111112900");
    }

    @Test
    void 세종은_시군구_없이_매칭되고_주소의_연속_공백도_허용한다() {
        assertThat(cd(resolver.byName("세종특별자치시", null, "반곡동", "세종특별자치시  반곡동 4121 세종 펠리스")))
                .isEqualTo("3611010100");
        assertThat(cd(resolver.byAddressPrefix("세종특별자치시  반곡동  수루배마을1단지"))).isEqualTo("3611010100");
    }

    @Test
    void 같은_시군구에_같은_이름의_리가_둘이면_주소의_읍면으로_좁힌다() {
        assertThat(cd(resolver.byName("전라남도", "여수시", "우두리", "전라남도 여수시 돌산읍 우두리 1132-1 청솔")))
                .isEqualTo("4617025021");
        assertThat(cd(resolver.byName("전라남도", "여수시", "우두리", "전라남도 여수시 소라면 우두리 12 단지")))
                .isEqualTo("4617031021");
    }

    @Test
    void 같은_이름의_리가_둘인데_주소로도_못_좁히면_매칭하지_않는다() {
        assertThat(resolver.byName("전라남도", "여수시", "우두리", "전라남도 여수시 우두리 12 단지")).isEmpty();
    }

    @Test
    void 주소_prefix는_가장_긴_leaf를_고른다() {
        assertThat(cd(resolver.byAddressPrefix("전라남도 여수시 돌산읍 우두리 1132-1 청솔"))).isEqualTo("4617025021");
    }

    @Test
    void dong_ri가_없으면_resolve는_주소_prefix로_읍면_단위까지_채운다() {
        assertThat(resolver.byName("전라남도", "여수시", null, "전라남도 여수시 돌산읍  484-28 단지")).isEmpty();
        assertThat(cd(resolver.resolve("전라남도", "여수시", null, "전라남도 여수시 돌산읍  484-28 단지")))
                .isEqualTo("4617025000");
    }

    @Test
    void 이름_매칭이_성공하면_resolve는_주소_prefix보다_이름을_우선한다() {
        // 실제 사례(complex_id=14826): 첫 주소가 "봉담읍  100-1,"로 리가 빠져 prefix는 읍 단위에서 멈춘다.
        String address = "경기도 화성효행구 봉담읍  100-1,경기도 화성효행구 봉담읍 동화리 100-1 클래식타운";
        assertThat(cd(resolver.byAddressPrefix(address))).isEqualTo("4159325000");
        assertThat(cd(resolver.resolve("경기도", "화성효행구", "동화리", address))).isEqualTo("4159325024");
    }

    @Test
    void 비활성_코드와_시군구_대표행은_후보가_아니다() {
        assertThat(resolver.byName("경기도", "화성시", "동화리", "경기도 화성시 봉담읍 동화리 1")).isEmpty();
        assertThat(resolver.byAddressPrefix("경기도 수원장안구 12 단지")).isEmpty();
    }
}
