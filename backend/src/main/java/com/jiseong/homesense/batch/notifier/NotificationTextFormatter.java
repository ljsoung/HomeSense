package com.jiseong.homesense.batch.notifier;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.RentType;

/**
 * 알림 제목·메시지(BAT-NTF-01 D3·D4). 금액은 모두 만원 단위 입력이다. 순수 함수만 있다.
 *
 * <p>제목은 MY-04 목록에, 메시지는 BAT-MAIL-01 이메일 본문의 소스로 쓰인다. notification.title은
 * VARCHAR(200), message는 VARCHAR(500)이라 넘치면 자른다 — 제목은 대상명만 줄여 뒤의 "신규 실거래 3건"·
 * "실거래가 2.1% 상승"이 남게 한다. 길이는 코드포인트 기준이다(MariaDB utf8mb4 VARCHAR는 문자 수 기준).
 */
final class NotificationTextFormatter {

    static final int TITLE_MAX = 200;
    static final int MESSAGE_MAX = 500;

    private static final String ELLIPSIS = "…";
    private static final DateTimeFormatter DEAL_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private NotificationTextFormatter() {
    }

    static String newTradeTitle(String targetName, int count) {
        return titleWithSuffix(targetName, " 신규 실거래 " + count + "건");
    }

    /** {@code 2026.09.30 계약 · 전용 84.97㎡ · 9층 · 매매 9억 5,000만원 외 2건} — 층이 없으면 그 구간을 뺀다. */
    static String newTradeMessage(NewTradeRow representative, int count) {
        StringBuilder sb = new StringBuilder();
        sb.append(representative.dealDate().format(DEAL_DATE)).append(" 계약");
        if (representative.excluUseArea() != null) {
            sb.append(" · 전용 ").append(area(representative.excluUseArea())).append("㎡");
        }
        if (representative.floor() != null) {
            sb.append(" · ").append(representative.floor()).append("층");
        }
        String amount = amountLabel(representative);
        if (amount != null) {
            sb.append(" · ").append(amount);
        }
        if (count > 1) {
            sb.append(" 외 ").append(count - 1).append("건");
        }
        return truncate(sb.toString(), MESSAGE_MAX);
    }

    static String priceChangeTitle(String targetName, BigDecimal roundedRate) {
        String direction = roundedRate.signum() > 0 ? "상승" : "하락";
        return titleWithSuffix(targetName,
                " 실거래가 " + roundedRate.abs().setScale(1, RoundingMode.HALF_UP).toPlainString() + "% " + direction);
    }

    static String priceChangeMessage(int baselineMonths, BigDecimal baselinePerPyeong, int newSaleCount,
                                     BigDecimal newPerPyeong) {
        return truncate("최근 " + baselineMonths + "개월 평균 3.3㎡당 " + manwon(baselinePerPyeong)
                + " → 신규 매매 " + newSaleCount + "건 평균 3.3㎡당 " + manwon(newPerPyeong), MESSAGE_MAX);
    }

    /** {@code 매매 9억 5,000만원} / {@code 전세 3억} / {@code 월세 1,000/50}. 금액이 없으면 null. */
    static String amountLabel(NewTradeRow trade) {
        if (trade.dealCategory() == DealCategory.SALE) {
            return trade.dealAmount() != null ? "매매 " + price(trade.dealAmount()) : null;
        }
        boolean wolse = trade.rentType() == RentType.WOLSE
                || (trade.rentType() == null && trade.monthlyRentAmount() != null && trade.monthlyRentAmount() > 0);
        if (wolse) {
            long deposit = trade.depositAmount() != null ? trade.depositAmount() : 0L;
            long monthly = trade.monthlyRentAmount() != null ? trade.monthlyRentAmount() : 0L;
            return "월세 " + grouped(deposit) + "/" + grouped(monthly);
        }
        return trade.depositAmount() != null ? "전세 " + price(trade.depositAmount()) : null;
    }

    /** 만원 단위 → {@code 9억 5,000만원} / {@code 3억} / {@code 8,500만원} / {@code 10억 5만원}. */
    static String price(long manwon) {
        long eok = manwon / 10_000;
        long rest = manwon % 10_000;
        if (eok == 0) {
            return grouped(rest) + "만원";
        }
        if (rest == 0) {
            return eok + "억";
        }
        return grouped(eok) + "억 " + grouped(rest) + "만원";
    }

    static String truncate(String text, int maxCodePoints) {
        if (text.codePointCount(0, text.length()) <= maxCodePoints) {
            return text;
        }
        int end = text.offsetByCodePoints(0, maxCodePoints - 1);
        return text.substring(0, end) + ELLIPSIS;
    }

    private static String titleWithSuffix(String targetName, String suffix) {
        String name = targetName != null ? targetName : "";
        int room = TITLE_MAX - suffix.codePointCount(0, suffix.length());
        return truncate(name, room) + suffix;
    }

    private static String manwon(BigDecimal value) {
        return grouped(value.setScale(0, RoundingMode.HALF_UP).longValueExact()) + "만원";
    }

    private static String area(BigDecimal area) {
        BigDecimal stripped = area.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    private static String grouped(long value) {
        return new DecimalFormat("#,##0").format(value);
    }
}
