package com.jiseong.homesense.batch.parser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.jiseong.homesense.batch.parser.dto.RawTradeItem;

/**
 * BAT-PRS-01. 국토부 실거래가 Open API 응답 XML을 item 단위 {@link RawTradeItem} 목록으로 파싱한다.
 * resultCode 판정은 BAT-CLC-01이 이미 수행하지만, 이 단계에서도 다시 확인해 이중 방어한다.
 */
@Component
public class TradeXmlParser {

    private static final String RESULT_CODE_SUCCESS = "000";
    private static final String RESULT_CODE_NO_DATA = "03";

    public List<RawTradeItem> parse(String xml) {
        Document document = parseDocument(xml);

        String resultCode = firstElementText(document, "resultCode");
        if (resultCode == null) {
            throw new TradeXmlParsingException("응답에 header/resultCode가 없다");
        }
        if (RESULT_CODE_NO_DATA.equals(resultCode)) {
            return List.of();
        }
        if (!RESULT_CODE_SUCCESS.equals(resultCode)) {
            throw new TradeXmlParsingException(
                    "resultCode=" + resultCode
                            + " — BAT-CLC-01에서 CONTINUE/RETRY/ABORT 판정이 끝났어야 할 응답이 파싱 단계까지 전달됐다");
        }

        NodeList itemNodes = document.getElementsByTagName("item");
        List<RawTradeItem> items = new ArrayList<>(itemNodes.getLength());
        for (int i = 0; i < itemNodes.getLength(); i++) {
            items.add(toRawTradeItem((Element) itemNodes.item(i)));
        }
        return items;
    }

    private RawTradeItem toRawTradeItem(Element itemElement) {
        Map<String, String> fields = new LinkedHashMap<>();
        NodeList children = itemElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String text = child.getTextContent();
                fields.put(child.getNodeName(), text == null ? "" : text.trim());
            }
        }
        return new RawTradeItem(fields);
    }

    private String firstElementText(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? null : text.trim();
    }

    private Document parseDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE 방지 — 외부 Open API 응답이라도 DTD·외부 엔티티는 신뢰하지 않는다.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new TradeXmlParsingException("XML 파싱에 실패했다", e);
        }
    }
}
