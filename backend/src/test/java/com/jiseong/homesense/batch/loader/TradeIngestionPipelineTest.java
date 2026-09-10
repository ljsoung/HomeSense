package com.jiseong.homesense.batch.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jiseong.homesense.batch.matcher.ComplexMasterMatcher;
import com.jiseong.homesense.batch.matcher.LegalDistrictMatcher;
import com.jiseong.homesense.batch.matcher.MatchResult;
import com.jiseong.homesense.batch.parser.MalformedTradeItemException;
import com.jiseong.homesense.batch.parser.TradeFieldMapper;
import com.jiseong.homesense.batch.parser.TradeXmlParser;
import com.jiseong.homesense.batch.parser.TradeXmlParsingException;
import com.jiseong.homesense.batch.parser.dto.RawTradeItem;
import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.region.entity.LegalDistrictCode;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.MatchMethod;

@ExtendWith(MockitoExtension.class)
class TradeIngestionPipelineTest {

    private static final String DATASET_ID = "15126468";

    @Mock
    private TradeXmlParser tradeXmlParser;

    @Mock
    private TradeFieldMapper tradeFieldMapper;

    @Mock
    private LegalDistrictMatcher legalDistrictMatcher;

    @Mock
    private ComplexMasterMatcher complexMasterMatcher;

    @Mock
    private TradeDataLoader tradeDataLoader;

    private TradeIngestionPipeline pipeline;

    private void setUpPipeline() {
        pipeline = new TradeIngestionPipeline(
                tradeXmlParser, tradeFieldMapper, legalDistrictMatcher, complexMasterMatcher, tradeDataLoader);
    }

    private static TradeDraft draftOf(String sggCd, String umdNm) {
        return new TradeDraft(HousingType.APT, DealCategory.SALE, null, DATASET_ID, sggCd, umdNm,
                "테스트단지", "123-4", new BigDecimal("84.99"), (short) 5, (short) 2005,
                LocalDate.of(2024, 1, 15), 100_000L, null, null, null, "AGENT", "서울 강남구",
                null, "개인", "개인", false, false, null,
                null, null, null, null);
    }

    private static LegalDistrictCode districtOf(String legalDongCd) {
        return LegalDistrictCode.builder()
                .legalDongCd(legalDongCd)
                .legalDongName("서울특별시 강남구 역삼동")
                .sidoName("서울특별시")
                .sigunguName("강남구")
                .eupmyeondongName("역삼동")
                .isActive(true)
                .build();
    }

    @Test
    void 미지원_조합이면_수집만_하고_파싱조차_시도하지_않는다() {
        setUpPipeline();
        when(tradeFieldMapper.supports(HousingType.APT, DealCategory.RENT)).thenReturn(false);

        LoadResult result = pipeline.process(HousingType.APT, DealCategory.RENT, DATASET_ID, List.of("<response/>"));

        assertThat(result).isEqualTo(new LoadResult(0, 0, 0, 0));
        verifyNoInteractions(tradeXmlParser, tradeDataLoader);
    }

    @Test
    void 페이지_파싱에_실패하면_그_페이지만_스킵하고_다른_페이지는_계속_처리한다() {
        setUpPipeline();
        when(tradeFieldMapper.supports(HousingType.APT, DealCategory.SALE)).thenReturn(true);
        when(tradeXmlParser.parse("bad")).thenThrow(new TradeXmlParsingException("파싱 실패"));
        RawTradeItem rawItem = new RawTradeItem(java.util.Map.of());
        when(tradeXmlParser.parse("good")).thenReturn(List.of(rawItem));
        TradeDraft draft = draftOf("11680", "역삼동");
        when(tradeFieldMapper.mapToUnifiedModel(rawItem, HousingType.APT, DealCategory.SALE, DATASET_ID))
                .thenReturn(draft);
        when(legalDistrictMatcher.matchByTradeSggCd(anyString(), any())).thenReturn(Optional.empty());
        when(complexMasterMatcher.matchComplex(any(), isNull())).thenReturn(MatchResult.unmatched());
        when(tradeDataLoader.loadBatch(any())).thenReturn(new LoadResult(1, 0, 1, 0));

        LoadResult result = pipeline.process(HousingType.APT, DealCategory.SALE, DATASET_ID, List.of("bad", "good"));

        assertThat(result).isEqualTo(new LoadResult(1, 1, 1, 0));
        verify(tradeDataLoader).loadBatch(List.of(draft.withMatch(null, null, null, null)));
    }

    @Test
    void 항목_매핑에_실패하면_그_항목만_스킵하고_에러로_집계한다() {
        setUpPipeline();
        when(tradeFieldMapper.supports(HousingType.APT, DealCategory.SALE)).thenReturn(true);
        RawTradeItem badItem = new RawTradeItem(java.util.Map.of());
        when(tradeXmlParser.parse("page")).thenReturn(List.of(badItem));
        when(tradeFieldMapper.mapToUnifiedModel(badItem, HousingType.APT, DealCategory.SALE, DATASET_ID))
                .thenThrow(new MalformedTradeItemException("필수 필드 없음"));
        when(tradeDataLoader.loadBatch(List.of())).thenReturn(new LoadResult(0, 0, 0, 0));

        LoadResult result = pipeline.process(HousingType.APT, DealCategory.SALE, DATASET_ID, List.of("page"));

        assertThat(result).isEqualTo(new LoadResult(0, 1, 0, 0));
        verify(tradeDataLoader).loadBatch(List.of());
    }

    @Test
    void 법정동_매칭에_성공하면_단지_매칭기에_엔티티를_그대로_넘기고_결과를_draft에_반영한다() {
        setUpPipeline();
        when(tradeFieldMapper.supports(HousingType.APT, DealCategory.SALE)).thenReturn(true);
        RawTradeItem rawItem = new RawTradeItem(java.util.Map.of());
        when(tradeXmlParser.parse("page")).thenReturn(List.of(rawItem));
        TradeDraft draft = draftOf("11680", "역삼동");
        when(tradeFieldMapper.mapToUnifiedModel(rawItem, HousingType.APT, DealCategory.SALE, DATASET_ID))
                .thenReturn(draft);
        LegalDistrictCode district = districtOf("1168010100");
        when(legalDistrictMatcher.matchByTradeSggCd("11680", "역삼동")).thenReturn(Optional.of(district));
        MatchResult matchResult = MatchResult.exact(999L, new BigDecimal("1.000"));
        when(complexMasterMatcher.matchComplex(draft, district)).thenReturn(matchResult);
        when(tradeDataLoader.loadBatch(any())).thenReturn(new LoadResult(1, 0, 1, 0));

        pipeline.process(HousingType.APT, DealCategory.SALE, DATASET_ID, List.of("page"));

        ArgumentCaptor<List<TradeDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(tradeDataLoader).loadBatch(captor.capture());
        TradeDraft loaded = captor.getValue().get(0);
        assertThat(loaded.legalDongCd()).isEqualTo("1168010100");
        assertThat(loaded.complexId()).isEqualTo(999L);
        assertThat(loaded.matchMethod()).isEqualTo(MatchMethod.EXACT);
        assertThat(loaded.matchConfidence()).isEqualByComparingTo(new BigDecimal("1.000"));
    }

    @Test
    void 법정동_매칭에_실패하면_단지_매칭기에_null을_그대로_넘기고_미매칭_결과를_draft에_반영한다() {
        setUpPipeline();
        when(tradeFieldMapper.supports(HousingType.APT, DealCategory.SALE)).thenReturn(true);
        RawTradeItem rawItem = new RawTradeItem(java.util.Map.of());
        when(tradeXmlParser.parse("page")).thenReturn(List.of(rawItem));
        TradeDraft draft = draftOf("11680", "알수없는동");
        when(tradeFieldMapper.mapToUnifiedModel(rawItem, HousingType.APT, DealCategory.SALE, DATASET_ID))
                .thenReturn(draft);
        when(legalDistrictMatcher.matchByTradeSggCd("11680", "알수없는동")).thenReturn(Optional.empty());
        when(complexMasterMatcher.matchComplex(draft, null)).thenReturn(MatchResult.unmatched());
        when(tradeDataLoader.loadBatch(any())).thenReturn(new LoadResult(1, 0, 1, 0));

        pipeline.process(HousingType.APT, DealCategory.SALE, DATASET_ID, List.of("page"));

        ArgumentCaptor<List<TradeDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(tradeDataLoader).loadBatch(captor.capture());
        TradeDraft loaded = captor.getValue().get(0);
        assertThat(loaded.legalDongCd()).isNull();
        assertThat(loaded.complexId()).isNull();
        assertThat(loaded.matchMethod()).isNull();
        verify(complexMasterMatcher).matchComplex(draft, null);
    }

    @Test
    void 로더_호출은_데이터셋당_한_번만_이뤄진다() {
        setUpPipeline();
        when(tradeFieldMapper.supports(HousingType.APT, DealCategory.SALE)).thenReturn(true);
        RawTradeItem item1 = new RawTradeItem(java.util.Map.of("id", "1"));
        RawTradeItem item2 = new RawTradeItem(java.util.Map.of("id", "2"));
        when(tradeXmlParser.parse("page1")).thenReturn(List.of(item1));
        when(tradeXmlParser.parse("page2")).thenReturn(List.of(item2));
        TradeDraft draft1 = draftOf("11680", "역삼동");
        TradeDraft draft2 = draftOf("11680", "역삼동");
        when(tradeFieldMapper.mapToUnifiedModel(item1, HousingType.APT, DealCategory.SALE, DATASET_ID))
                .thenReturn(draft1);
        when(tradeFieldMapper.mapToUnifiedModel(item2, HousingType.APT, DealCategory.SALE, DATASET_ID))
                .thenReturn(draft2);
        when(legalDistrictMatcher.matchByTradeSggCd(anyString(), any())).thenReturn(Optional.empty());
        when(complexMasterMatcher.matchComplex(any(), isNull())).thenReturn(MatchResult.unmatched());
        when(tradeDataLoader.loadBatch(any())).thenReturn(new LoadResult(2, 0, 2, 0));

        pipeline.process(HousingType.APT, DealCategory.SALE, DATASET_ID, List.of("page1", "page2"));

        verify(tradeDataLoader, never()).loadBatch(List.of());
        ArgumentCaptor<List<TradeDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(tradeDataLoader).loadBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }
}
