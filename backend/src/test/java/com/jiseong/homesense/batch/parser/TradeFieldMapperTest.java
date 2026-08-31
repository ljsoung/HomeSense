package com.jiseong.homesense.batch.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.batch.parser.dto.RawTradeItem;
import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;

class TradeFieldMapperTest {

    private final TradeFieldMapper mapper = new TradeFieldMapper();

    private static RawTradeItem itemWith(Map<String, String> overrides) {
        Map<String, String> fields = new HashMap<>();
        fields.put("sggCd", "11680");
        fields.put("umdNm", "역삼동");
        fields.put("aptNm", "역삼래미안");
        fields.put("jibun", "123-4");
        fields.put("excluUseAr", "84.99");
        fields.put("dealYear", "2024");
        fields.put("dealMonth", "1");
        fields.put("dealDay", "15");
        fields.put("dealAmount", "120,000");
        fields.put("floor", "10");
        fields.put("buildYear", "2005");
        fields.put("dealingGbn", "중개거래");
        fields.put("estateAgentSggNm", "서울 강남구");
        fields.put("rgstDate", "24.01.20");
        fields.put("aptDong", " ");
        fields.put("slerGbn", "개인");
        fields.put("buyerGbn", "개인");
        fields.put("landLeaseholdGbn", "N");
        fields.put("cdealType", " ");
        fields.put("cdealDay", "");
        fields.putAll(overrides);
        return new RawTradeItem(fields);
    }

    @Test
    void 아파트_매매_필드를_통합_스키마로_매핑한다() {
        TradeDraft draft = mapper.mapToUnifiedModel(itemWith(Map.of()), HousingType.APT, DealCategory.SALE, "15126468");

        assertThat(draft.housingType()).isEqualTo(HousingType.APT);
        assertThat(draft.dealCategory()).isEqualTo(DealCategory.SALE);
        assertThat(draft.rentType()).isNull();
        assertThat(draft.datasetId()).isEqualTo("15126468");
        assertThat(draft.sggCd()).isEqualTo("11680");
        assertThat(draft.umdNm()).isEqualTo("역삼동");
        assertThat(draft.buildingName()).isEqualTo("역삼래미안");
        assertThat(draft.jibun()).isEqualTo("123-4");
        assertThat(draft.excluUseArea()).isEqualByComparingTo(new BigDecimal("84.99"));
        assertThat(draft.floor()).isEqualTo((short) 10);
        assertThat(draft.buildYear()).isEqualTo((short) 2005);
        assertThat(draft.dealDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(draft.dealAmount()).isEqualTo(120_000L);
        assertThat(draft.depositAmount()).isNull();
        assertThat(draft.monthlyRentAmount()).isNull();
        assertThat(draft.aptDong()).isNull();
        assertThat(draft.dealingType()).isEqualTo("AGENT");
        assertThat(draft.agentSggNm()).isEqualTo("서울 강남구");
        assertThat(draft.registrationDate()).isEqualTo(LocalDate.of(2024, 1, 20));
        assertThat(draft.sellerType()).isEqualTo("개인");
        assertThat(draft.buyerType()).isEqualTo("개인");
        assertThat(draft.landLeaseYn()).isFalse();
        assertThat(draft.cancelYn()).isFalse();
        assertThat(draft.cancelDate()).isNull();
    }

    @Test
    void 콤마가_포함된_거래금액을_정수로_변환한다() {
        TradeDraft draft = mapper.mapToUnifiedModel(
                itemWith(Map.of("dealAmount", "1,234,500")), HousingType.APT, DealCategory.SALE, "15126468");

        assertThat(draft.dealAmount()).isEqualTo(1_234_500L);
    }

    @Test
    void 직거래는_DIRECT로_변환한다() {
        TradeDraft draft = mapper.mapToUnifiedModel(
                itemWith(Map.of("dealingGbn", "직거래")), HousingType.APT, DealCategory.SALE, "15126468");

        assertThat(draft.dealingType()).isEqualTo("DIRECT");
    }

    @Test
    void 공백인_아파트동은_null로_변환한다() {
        TradeDraft draft = mapper.mapToUnifiedModel(itemWith(Map.of()), HousingType.APT, DealCategory.SALE, "15126468");

        assertThat(draft.aptDong()).isNull();
    }

    @Test
    void 아파트동_값이_있으면_trim해서_보존한다() {
        TradeDraft draft = mapper.mapToUnifiedModel(
                itemWith(Map.of("aptDong", " 101동 ")), HousingType.APT, DealCategory.SALE, "15126468");

        assertThat(draft.aptDong()).isEqualTo("101동");
    }

    @Test
    void 해제사유가_있으면_해제여부와_해제일자를_채운다() {
        TradeDraft draft = mapper.mapToUnifiedModel(
                itemWith(Map.of("cdealType", "해제", "cdealDay", "24.03.02")),
                HousingType.APT, DealCategory.SALE, "15126468");

        assertThat(draft.cancelYn()).isTrue();
        assertThat(draft.cancelDate()).isEqualTo(LocalDate.of(2024, 3, 2));
    }

    @Test
    void 지목이_대지권_지분이면_landLeaseYn을_true로_변환한다() {
        TradeDraft draft = mapper.mapToUnifiedModel(
                itemWith(Map.of("landLeaseholdGbn", "Y")), HousingType.APT, DealCategory.SALE, "15126468");

        assertThat(draft.landLeaseYn()).isTrue();
    }

    @Test
    void 층과_건축년도가_공백이면_null로_변환한다() {
        TradeDraft draft = mapper.mapToUnifiedModel(
                itemWith(Map.of("floor", " ", "buildYear", " ")), HousingType.APT, DealCategory.SALE, "15126468");

        assertThat(draft.floor()).isNull();
        assertThat(draft.buildYear()).isNull();
    }

    @Test
    void 층을_숫자로_변환할_수_없으면_MalformedTradeItemException을_던진다() {
        RawTradeItem invalidFloor = itemWith(Map.of("floor", "십층"));

        assertThatThrownBy(() -> mapper.mapToUnifiedModel(invalidFloor, HousingType.APT, DealCategory.SALE, "15126468"))
                .isInstanceOf(MalformedTradeItemException.class);
    }

    @Test
    void 필수_필드가_없으면_MalformedTradeItemException을_던진다() {
        RawTradeItem missingSggCd = itemWith(Map.of("sggCd", ""));

        assertThatThrownBy(() -> mapper.mapToUnifiedModel(missingSggCd, HousingType.APT, DealCategory.SALE, "15126468"))
                .isInstanceOf(MalformedTradeItemException.class);
    }

    @Test
    void 전용면적을_숫자로_변환할_수_없으면_MalformedTradeItemException을_던진다() {
        RawTradeItem invalidArea = itemWith(Map.of("excluUseAr", "N/A"));

        assertThatThrownBy(() -> mapper.mapToUnifiedModel(invalidArea, HousingType.APT, DealCategory.SALE, "15126468"))
                .isInstanceOf(MalformedTradeItemException.class);
    }

    @Test
    void 거래연월일_조합이_유효하지_않으면_MalformedTradeItemException을_던진다() {
        RawTradeItem invalidDate = itemWith(Map.of("dealMonth", "13"));

        assertThatThrownBy(() -> mapper.mapToUnifiedModel(invalidDate, HousingType.APT, DealCategory.SALE, "15126468"))
                .isInstanceOf(MalformedTradeItemException.class);
    }

    @Test
    void 전월세_데이터셋은_아직_구현하지_않아_예외를_던진다() {
        RawTradeItem item = itemWith(Map.of());

        assertThatThrownBy(() -> mapper.mapToUnifiedModel(item, HousingType.APT, DealCategory.RENT, "15126474"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 연립다세대는_단지명_태그가_미확정이라_아파트_태그로_조용히_매핑하지_않고_예외를_던진다() {
        // 회귀 테스트: housingType을 검사하지 않던 시절에는 VILLA 매매도 aptNm을 그대로 읽어
        // buildingName == null인 채로 "성공"한 TradeDraft를 만들어냈고, 그 결과 BAT-MAT-02의
        // 단지명 매칭(EXACT 재확인·SIMILAR)이 VILLA 건에 대해 항상 무력화됐다.
        RawTradeItem item = itemWith(Map.of());

        assertThatThrownBy(() -> mapper.mapToUnifiedModel(item, HousingType.VILLA, DealCategory.SALE, "15126467"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
