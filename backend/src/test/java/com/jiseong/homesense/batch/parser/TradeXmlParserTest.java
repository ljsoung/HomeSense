package com.jiseong.homesense.batch.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jiseong.homesense.batch.parser.dto.RawTradeItem;

class TradeXmlParserTest {

    private final TradeXmlParser parser = new TradeXmlParser();

    @Test
    void 정상_응답이면_item_단위로_파싱한다() {
        String xml = """
                <response>
                    <header>
                        <resultCode>000</resultCode>
                        <resultMsg>NORMAL SERVICE.</resultMsg>
                    </header>
                    <body>
                        <items>
                            <item>
                                <sggCd>11680</sggCd>
                                <umdNm>역삼동</umdNm>
                                <aptNm>역삼래미안</aptNm>
                                <jibun>123-4</jibun>
                                <dealAmount>120,000</dealAmount>
                            </item>
                            <item>
                                <sggCd>11680</sggCd>
                                <umdNm>대치동</umdNm>
                                <aptNm>대치아이파크</aptNm>
                                <jibun>45-6</jibun>
                                <dealAmount>150,000</dealAmount>
                            </item>
                        </items>
                        <numOfRows>10</numOfRows>
                        <pageNo>1</pageNo>
                        <totalCount>2</totalCount>
                    </body>
                </response>
                """;

        List<RawTradeItem> items = parser.parse(xml);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("umdNm")).isEqualTo("역삼동");
        assertThat(items.get(0).get("dealAmount")).isEqualTo("120,000");
        assertThat(items.get(1).get("aptNm")).isEqualTo("대치아이파크");
    }

    @Test
    void resultCode가_03이면_빈_결과로_처리하고_item을_찾지_않는다() {
        String xml = """
                <response>
                    <header>
                        <resultCode>03</resultCode>
                        <resultMsg>NO_DATA</resultMsg>
                    </header>
                    <body></body>
                </response>
                """;

        List<RawTradeItem> items = parser.parse(xml);

        assertThat(items).isEmpty();
    }

    @Test
    void resultCode가_000_03이_아니면_이중_방어로_예외를_던진다() {
        String xml = """
                <response>
                    <header>
                        <resultCode>30</resultCode>
                        <resultMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</resultMsg>
                    </header>
                    <body></body>
                </response>
                """;

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(TradeXmlParsingException.class)
                .hasMessageContaining("30");
    }

    @Test
    void header가_없으면_예외를_던진다() {
        String xml = "<response><body></body></response>";

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(TradeXmlParsingException.class);
    }

    @Test
    void XML_형식이_깨지면_예외를_던진다() {
        String xml = "<response><header>";

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(TradeXmlParsingException.class);
    }
}
