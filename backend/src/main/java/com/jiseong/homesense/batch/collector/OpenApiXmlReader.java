package com.jiseong.homesense.batch.collector;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * 페이지네이션·에러코드 판정에 필요한 header/resultCode, totalCount만 가볍게 읽는다.
 * item 파싱은 BAT-PRS-01(TradeXmlParser) 책임이라 여기서는 하지 않는다.
 */
final class OpenApiXmlReader {

    private OpenApiXmlReader() {
    }

    static PageMeta readMeta(String xml, String datasetId) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE 방지 — 외부 Open API 응답이라도 DTD·외부 엔티티는 신뢰하지 않는다.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));

            String resultCode = textOf(document, "resultCode");
            if (resultCode == null) {
                throw new OpenApiResponseException("dataset=" + datasetId + " 응답에 header/resultCode가 없다");
            }
            int totalCount = parseIntOrZero(textOf(document, "totalCount"));
            return new PageMeta(resultCode, totalCount);
        } catch (OpenApiResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenApiResponseException("dataset=" + datasetId + " 응답 XML을 읽을 수 없다", e);
        }
    }

    private static String textOf(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? null : text.trim();
    }

    private static int parseIntOrZero(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
