package com.jiseong.homesense.batch.notifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.trade.entity.DealCategory;
import com.jiseong.homesense.trade.entity.RentType;

class NotificationTextFormatterTest {

    private static NewTradeRow trade(DealCategory category, RentType rentType, Long deal, Long deposit, Long monthly,
                                     String area, Short floor) {
        return new NewTradeRow(1L, 10L, "4111110100", LocalDate.of(2026, 9, 30), category, rentType,
                deal, deposit, monthly, area != null ? new BigDecimal(area) : null, floor);
    }

    @Test
    void 금액은_억과_만원으로_나눠_쓴다() {
        assertThat(NotificationTextFormatter.price(10_000)).isEqualTo("1억");
        assertThat(NotificationTextFormatter.price(9_999)).isEqualTo("9,999만원");
        assertThat(NotificationTextFormatter.price(100_005)).isEqualTo("10억 5만원");
        assertThat(NotificationTextFormatter.price(95_000)).isEqualTo("9억 5,000만원");
        assertThat(NotificationTextFormatter.price(30_000)).isEqualTo("3억");
        assertThat(NotificationTextFormatter.price(8_500)).isEqualTo("8,500만원");
        assertThat(NotificationTextFormatter.price(1_250_000)).isEqualTo("125억");
    }

    @Test
    void 매매_전세_월세_금액_표기() {
        assertThat(NotificationTextFormatter.amountLabel(trade(DealCategory.SALE, null, 95_000L, null, null, "84.97", null)))
                .isEqualTo("매매 9억 5,000만원");
        assertThat(NotificationTextFormatter.amountLabel(trade(DealCategory.RENT, RentType.JEONSE, null, 30_000L, 0L, "59", null)))
                .isEqualTo("전세 3억");
        assertThat(NotificationTextFormatter.amountLabel(trade(DealCategory.RENT, RentType.WOLSE, null, 1_000L, 50L, "59", null)))
                .isEqualTo("월세 1,000/50");
    }

    @Test
    void 신규거래_메시지는_대표거래와_나머지_건수를_쓴다() {
        NewTradeRow sale = trade(DealCategory.SALE, null, 95_000L, null, null, "84.9700", (short) 9);

        assertThat(NotificationTextFormatter.newTradeMessage(sale, 3))
                .isEqualTo("2026.09.30 계약 · 전용 84.97㎡ · 9층 · 매매 9억 5,000만원 외 2건");
        assertThat(NotificationTextFormatter.newTradeMessage(sale, 1))
                .isEqualTo("2026.09.30 계약 · 전용 84.97㎡ · 9층 · 매매 9억 5,000만원");
    }

    @Test
    void 층이_없으면_층_구간을_뺀다() {
        NewTradeRow jeonse = trade(DealCategory.RENT, RentType.JEONSE, null, 30_000L, 0L, "59.00", null);

        assertThat(NotificationTextFormatter.newTradeMessage(jeonse, 1)).isEqualTo("2026.09.30 계약 · 전용 59㎡ · 전세 3억");
    }

    @Test
    void 제목() {
        assertThat(NotificationTextFormatter.newTradeTitle("힐스테이트 안성", 2)).isEqualTo("힐스테이트 안성 신규 실거래 2건");
        assertThat(NotificationTextFormatter.priceChangeTitle("힐스테이트 안성", new BigDecimal("2.1")))
                .isEqualTo("힐스테이트 안성 실거래가 2.1% 상승");
        assertThat(NotificationTextFormatter.priceChangeTitle("힐스테이트 안성", new BigDecimal("-5")))
                .isEqualTo("힐스테이트 안성 실거래가 5.0% 하락");
    }

    @Test
    void 가격변동_메시지() {
        assertThat(NotificationTextFormatter.priceChangeMessage(3, new BigDecimal("4520.4"), 2, new BigDecimal("12345.6")))
                .isEqualTo("최근 3개월 평균 3.3㎡당 4,520만원 → 신규 매매 2건 평균 3.3㎡당 12,346만원");
    }

    @Test
    void 대상명이_길면_대상명만_잘라_제목이_200자를_넘지_않는다() {
        String longName = "가".repeat(300);

        String title = NotificationTextFormatter.newTradeTitle(longName, 12);

        assertThat(title.codePointCount(0, title.length())).isEqualTo(NotificationTextFormatter.TITLE_MAX);
        assertThat(title).endsWith("… 신규 실거래 12건");
    }

    @Test
    void 메시지는_500자에서_자르고_코드포인트를_깨지_않는다() {
        String text = "😀".repeat(600);

        String truncated = NotificationTextFormatter.truncate(text, NotificationTextFormatter.MESSAGE_MAX);

        assertThat(truncated.codePointCount(0, truncated.length())).isEqualTo(500);
        assertThat(truncated).endsWith("😀…");
        assertThat(NotificationTextFormatter.truncate("짧은 글", 500)).isEqualTo("짧은 글");
    }
}
