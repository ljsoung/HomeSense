package com.jiseong.homesense.batch.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import com.jiseong.homesense.batch.parser.dto.RawTradeItem;
import com.jiseong.homesense.batch.parser.dto.TradeDraft;
import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.HousingType;
import com.jiseong.homesense.trade.entity.RentType;

/**
 * BAT-PRS-01. RawTradeItem을 통합 스키마(TradeDraft)로 변환한다.
 * 아파트 매매(APT/SALE)는 「국토교통부 실거래가 정보 오픈API 활용가이드」로 확정된 필드명을 쓴다.
 * 아파트 전월세(APT/RENT)는 data.go.kr 공식 Swagger 스펙(15126474,
 * {@code https://www.data.go.kr/data/15126474/openapi.do}에 임베딩된 swaggerJson)을 직접 조회해
 * 확정했다 — deposit(보증금액)·monthlyRent(월세금액) 등 실제 필드명을 이번에 검증했다(CLAUDE.md
 * "버그 3" 절 참고). 연립다세대(VILLA)는 매매·전월세 둘 다 아직 이 클래스에서 구현하지 않는다 —
 * 전월세(15126473, RTMSDataSvcRHRent)의 단지명 태그가 mhouseNm이라는 것은 이번에 함께 확인했지만
 * (CLAUDE.md에 기록), 요구사항정의서 9장 2단계 로드맵이 "연립다세대는 SALE/RENT를 나눠 순차
 * 활성화하지 않고 housingType 파라미터 범위를 APT에서 APT,VILLA로 한 번에 넓힌다"고 명시해 SALE·
 * RENT를 같은 시점에 함께 연다 — RENT만 먼저 열면 그 원칙이 깨진다(VILLA/SALE은 애초에 SALE 전용
 * 필드 구성도 미재검증 상태). 2단계 착수 시 VILLA/SALE 필드명을 마저 확정하면서 이 클래스에 VILLA
 * 분기를 함께 추가하라 — mhouseNm은 그때 CLAUDE.md에서 바로 가져다 쓰면 된다.
 */
@Component
public class TradeFieldMapper {

    /**
     * rgstDate·cdealDay는 국토부 API 관례상 "yy.MM.dd" 형식으로 온다.
     * 다른 형식으로 확인되면 이 포맷터만 교체하면 된다.
     */
    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("yy.MM.dd");

    /**
     * BAT-LOD-01 파이프라인(TradeIngestionPipeline)이 mapToUnifiedModel() 호출 전에 확인하는 게이트 —
     * 여기서 false가 나오는 조합은 수집(BAT-CLC-01)·batch_log 기록은 그대로 하되 파싱 이후 단계는
     * 건너뛴다. RENT는 APT만 true다 — VILLA/RENT는 필드명(mhouseNm 등)을 이미 확인했지만, 2단계
     * 로드맵이 연립다세대는 SALE·RENT를 함께 여는 것을 전제하므로 VILLA/SALE과 같은 시점에 함께
     * 활성화한다(클래스 javadoc 참고). 아파트 전월세가 확정되면서 이 한 줄만 남았다 — VILLA 전체가
     * 마저 확정되면 이 줄만 넓히면 되고, 파이프라인/오케스트레이터 쪽은 코드 변경이 필요 없다.
     */
    public boolean supports(HousingType housingType, DealCategory dealCategory) {
        if (dealCategory == DealCategory.RENT) {
            return housingType == HousingType.APT;
        }
        return housingType == HousingType.APT && dealCategory == DealCategory.SALE;
    }

    public TradeDraft mapToUnifiedModel(
            RawTradeItem item, HousingType housingType, DealCategory dealCategory, String datasetId) {
        if (!supports(housingType, dealCategory)) {
            throw new UnsupportedOperationException(
                    "현재는 아파트 매매(APT/SALE)와 아파트 전월세(APT/RENT) 필드 매핑만 확정됐다 —"
                            + " 연립다세대(VILLA)는 매매·전월세 모두 2단계 로드맵에서 함께 활성화할"
                            + " 예정이라 아직 구현하지 않았다 (요구사항정의서 4.2절 각주, 9장 2단계)");
        }
        if (dealCategory == DealCategory.RENT) {
            return mapRentToUnifiedModel(item, housingType, dealCategory, datasetId);
        }

        String sggCd = required(item, "sggCd");
        BigDecimal excluUseArea = requiredDecimal(item, "excluUseAr");
        LocalDate dealDate = mapDealDate(item);
        Long dealAmount = requiredAmount(item, "dealAmount");

        return new TradeDraft(
                housingType,
                dealCategory,
                null, // rentType — SALE 행은 항상 NULL
                datasetId,
                sggCd,
                item.get("umdNm"),
                item.get("aptNm"),
                item.get("jibun"),
                excluUseArea,
                optionalShort(item, "floor"),
                optionalShort(item, "buildYear"),
                dealDate,
                dealAmount,
                null, // depositAmount — SALE 행은 항상 NULL
                null, // monthlyRentAmount — SALE 행은 항상 NULL
                nullIfBlank(item.get("aptDong")),
                mapDealingType(item.get("dealingGbn")),
                item.get("estateAgentSggNm"),
                parseLegacyDate(item.get("rgstDate"), "rgstDate"),
                item.get("slerGbn"),
                item.get("buyerGbn"),
                mapYn(item.get("landLeaseholdGbn")),
                !isBlank(item.get("cdealType")),
                parseLegacyDate(item.get("cdealDay"), "cdealDay"),
                null, // complexId — BAT-MAT-02가 채움
                null, // legalDongCd — BAT-MAT-01이 채움
                null, // matchMethod — BAT-MAT-02가 채움
                null); // matchConfidence — BAT-MAT-02가 채움
    }

    /**
     * 아파트 전월세(APT/RENT) 전용 매핑. data.go.kr 공식 Swagger 스펙(15126474)으로 확정한 필드만
     * 다룬다 — {@code supports()}가 RENT는 APT만 통과시키므로 이 메서드는 항상 housingType=APT로만
     * 호출된다(연립다세대는 2단계에서 SALE과 함께 활성화, 클래스 javadoc 참고). {@code deposit}은
     * 항상 필수(보증금), {@code monthlyRent}는 "0"이면 전세(JEONSE, monthlyRentAmount는 NULL로
     * 둔다 — 월세 자체가 없으므로), 0보다 크면 월세(WOLSE)다. RENT API 응답에는 SALE 전용 필드
     * (aptDong/dealingGbn/estateAgentSggNm/rgstDate/slerGbn/buyerGbn/landLeaseholdGbn/cdealType/
     * cdealDay)가 아예 없어(공식 스펙에 정의되지 않음) 전부 null/false로 둔다. contractTerm/
     * contractType/useRRRight/preDeposit/preMonthlyRent는 trade 테이블에 대응 컬럼이 없어 매핑하지
     * 않는다(CLAUDE.md "사용하지 않는 컬럼은 추가하지 않는다" 원칙).
     */
    private TradeDraft mapRentToUnifiedModel(
            RawTradeItem item, HousingType housingType, DealCategory dealCategory, String datasetId) {
        String sggCd = required(item, "sggCd");
        BigDecimal excluUseArea = requiredDecimal(item, "excluUseAr");
        LocalDate dealDate = mapDealDate(item);
        Long depositAmount = requiredAmount(item, "deposit");
        Long monthlyRentRaw = requiredAmount(item, "monthlyRent");
        boolean isJeonse = monthlyRentRaw == 0L;

        return new TradeDraft(
                housingType,
                dealCategory,
                isJeonse ? RentType.JEONSE : RentType.WOLSE,
                datasetId,
                sggCd,
                item.get("umdNm"),
                item.get("aptNm"),
                item.get("jibun"),
                excluUseArea,
                optionalShort(item, "floor"),
                optionalShort(item, "buildYear"),
                dealDate,
                null, // dealAmount — RENT 행은 항상 NULL
                depositAmount,
                isJeonse ? null : monthlyRentRaw,
                null, // aptDong — RENT API에 없는 필드
                null, // dealingType — RENT API에 없는 필드
                null, // agentSggNm — RENT API에 없는 필드
                null, // registrationDate — RENT API에 없는 필드
                null, // sellerType — RENT API에 없는 필드
                null, // buyerType — RENT API에 없는 필드
                null, // landLeaseYn — RENT API에 없는 필드
                false, // cancelYn — RENT API에 해제(cdealType) 개념 자체가 없음
                null, // cancelDate
                null, // complexId — BAT-MAT-02가 채움
                null, // legalDongCd — BAT-MAT-01이 채움
                null, // matchMethod — BAT-MAT-02가 채움
                null); // matchConfidence — BAT-MAT-02가 채움
    }

    private LocalDate mapDealDate(RawTradeItem item) {
        int year = requiredInt(item, "dealYear");
        int month = requiredInt(item, "dealMonth");
        int day = requiredInt(item, "dealDay");
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            throw new MalformedTradeItemException(
                    "dealYear/dealMonth/dealDay 조합이 유효한 날짜가 아니다: " + year + "-" + month + "-" + day);
        }
    }

    private String mapDealingType(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        return switch (raw.trim()) {
            case "중개거래" -> "AGENT";
            case "직거래" -> "DIRECT";
            default -> null;
        };
    }

    private Boolean mapYn(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "Y" -> Boolean.TRUE;
            case "N" -> Boolean.FALSE;
            default -> null;
        };
    }

    private LocalDate parseLegacyDate(String raw, String tagName) {
        String trimmed = nullIfBlank(raw);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed, LEGACY_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new MalformedTradeItemException(tagName + " 값을 날짜로 변환할 수 없다: " + trimmed);
        }
    }

    private String required(RawTradeItem item, String tagName) {
        String value = item.get(tagName);
        if (isBlank(value)) {
            throw new MalformedTradeItemException(tagName + " 필드가 없다");
        }
        return value;
    }

    private int requiredInt(RawTradeItem item, String tagName) {
        String raw = required(item, tagName);
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new MalformedTradeItemException(tagName + " 값을 숫자로 변환할 수 없다: " + raw);
        }
    }

    private Short optionalShort(RawTradeItem item, String tagName) {
        String raw = nullIfBlank(item.get(tagName));
        if (raw == null) {
            return null;
        }
        try {
            return Short.parseShort(raw);
        } catch (NumberFormatException e) {
            throw new MalformedTradeItemException(tagName + " 값을 숫자로 변환할 수 없다: " + raw);
        }
    }

    private BigDecimal requiredDecimal(RawTradeItem item, String tagName) {
        String raw = required(item, tagName);
        try {
            return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new MalformedTradeItemException(tagName + " 값을 숫자로 변환할 수 없다: " + raw);
        }
    }

    private Long requiredAmount(RawTradeItem item, String tagName) {
        String raw = required(item, tagName).replace(",", "").trim();
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new MalformedTradeItemException(tagName + " 값을 숫자로 변환할 수 없다: " + raw);
        }
    }

    private String nullIfBlank(String raw) {
        return isBlank(raw) ? null : raw.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
