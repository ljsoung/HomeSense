package com.jiseong.homesense.batch.loader;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BAT-PRS-01(TradeXmlParser/TradeFieldMapper) → BAT-MAT-01/02(LegalDistrictMatcher/ComplexMasterMatcher)
 * → BAT-LOD-01(TradeDataLoader)을 데이터셋 하나(한 조합의 기본/상세 데이터셋 각각) 단위로 체이닝한다.
 *
 * <p>데이터셋 단위로 나눠 부르는 이유: APT+SALE은 기본(15126469)·상세(15126468) 두 데이터셋이 있고
 * {@code ApiResponseXml}이 이 둘을 한 조합으로 묶어 넘긴다. 이 둘을 합쳐 loadBatch()를 한 번만 부르는
 * 대신 데이터셋별로 별도 호출하는 이유는, {@code DedupHashCalculator}가 datasetId를 해시에 넣지 않아
 * 같은 거래가 두 데이터셋에 나타나도 같은 dedup_hash로 수렴하고 {@code TradeChunkLoader.upsertOne()}이
 * 이미 "먼저 들어온 건 INSERT, 나중 건 UPDATE"를 보장하기 때문이다 — 나눠 불러도 합쳐 부르는 것과 최종
 * 결과가 같다. 대신 {@code batch_log}가 이미 데이터셋 단위 행(dataset_id 컬럼)이라 데이터셋별로 나누면
 * 정확한 processedCount/errorCount를 그대로 기록할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeIngestionPipeline {

    private final TradeXmlParser tradeXmlParser;
    private final TradeFieldMapper tradeFieldMapper;
    private final LegalDistrictMatcher legalDistrictMatcher;
    private final ComplexMasterMatcher complexMasterMatcher;
    private final TradeDataLoader tradeDataLoader;

    public LoadResult process(HousingType housingType, DealCategory dealCategory, String datasetId, List<String> pageBodies) {
        if (!tradeFieldMapper.supports(housingType, dealCategory)) {
            log.info("BAT-LOD-01 필드 매핑 미구현 조합 — 수집만 하고 적재는 건너뜀: housingType={}, dealCategory={}, datasetId={}",
                    housingType, dealCategory, datasetId);
            return LoadResult.empty();
        }

        List<TradeDraft> drafts = new ArrayList<>();
        int mappingErrors = 0;

        for (String pageBody : pageBodies) {
            List<RawTradeItem> items;
            try {
                items = tradeXmlParser.parse(pageBody);
            } catch (TradeXmlParsingException e) {
                mappingErrors++;
                log.error("BAT-PRS-01 페이지 파싱 실패, 이 페이지만 스킵한다: datasetId={}", datasetId, e);
                continue;
            }

            for (RawTradeItem item : items) {
                try {
                    TradeDraft draft = tradeFieldMapper.mapToUnifiedModel(item, housingType, dealCategory, datasetId);
                    drafts.add(match(draft));
                } catch (MalformedTradeItemException e) {
                    mappingErrors++;
                    log.error("BAT-PRS-01 항목 매핑 실패, 이 항목만 스킵한다: datasetId={}", datasetId, e);
                }
            }
        }

        LoadResult loadResult = tradeDataLoader.loadBatch(drafts);
        return mappingErrors == 0 ? loadResult : loadResult.merge(new LoadResult(0, mappingErrors, 0, 0));
    }

    /**
     * 법정동/단지 매칭 실패는 에러가 아니라 정상적인 미매칭 결과다(FR-2.5 목표 성공률 98.9%가 이미
     * 100% 미만을 전제) — draft는 그대로 로더에 넘어가 complex_id/legal_dong_cd가 NULL인 채로
     * 적재된다({@code TradeChunkLoader}의 legalDistrictCodeReference()/complexReference()가 이미
     * null-safe). ComplexMasterMatcher.matchComplex()는 legalDistrictCode가 null이면 이미 "법정동코드
     * 매핑 실패" 사유로 로깅까지 마친 unmatched()를 반환하므로 여기서 별도 null 분기가 필요 없다.
     */
    private TradeDraft match(TradeDraft draft) {
        LegalDistrictCode legalDistrictCode =
                legalDistrictMatcher.matchByTradeSggCd(draft.sggCd(), draft.umdNm()).orElse(null);
        MatchResult matchResult = complexMasterMatcher.matchComplex(draft, legalDistrictCode);
        String legalDongCd = legalDistrictCode != null ? legalDistrictCode.getLegalDongCd() : null;
        return draft.withMatch(legalDongCd, matchResult.complexId(), matchResult.matchMethod(), matchResult.matchConfidence());
    }
}
