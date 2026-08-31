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

/**
 * BAT-PRS-01. RawTradeItem을 통합 스키마(TradeDraft)로 변환한다.
 * 필드 매핑은 「국토교통부 실거래가 정보 오픈API 활용가이드(아파트 매매)」로 확정된 값만 다룬다.
 * 전월세(RENT)는 보증금·월세금액 계열 필드명이 아직 기술문서로 재검증되지 않아 구현하지 않았다
 * (요구사항정의서 4.2절 각주 — 신규 제안이 아니라 원본 전제 조건을 그대로 승계).
 */
@Component
public class TradeFieldMapper {

    /**
     * rgstDate·cdealDay는 국토부 API 관례상 "yy.MM.dd" 형식으로 온다.
     * 다른 형식으로 확인되면 이 포맷터만 교체하면 된다.
     */
    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("yy.MM.dd");

    public TradeDraft mapToUnifiedModel(
            RawTradeItem item, HousingType housingType, DealCategory dealCategory, String datasetId) {
        if (dealCategory != DealCategory.SALE) {
            throw new UnsupportedOperationException(
                    "전월세(RENT) 필드 매핑은 기술문서 재검증 전이라 아직 구현하지 않았다 — 요구사항정의서 4.2절 각주");
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
                parseLegacyDate(item.get("cdealDay"), "cdealDay"));
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
